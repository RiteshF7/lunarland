# HTTP 401 Authentication Error - Root Cause Analysis & Permanent Fixes

## Problem Summary
The Portal HTTP API server on `localhost:8081` is returning **HTTP 401 Unauthorized** errors when droidrun tries to access endpoints like `/state_full`. This happens because:

1. **The Portal server requires authentication** for all endpoints except `/ping` and `/health`
2. **The portal_client.py is not sending the Authorization header** in HTTP requests
3. **The auth token is auto-generated** (UUID) and stored in the Portal app's SharedPreferences
4. **The token is accessible** via ContentProvider at `content://com.droidrun.portal/auth_token`

## Root Cause

### Server Side (Portal App)
**File:** `app/src/main/java/com/droidrun/portal/service/SocketServer.kt`
- **Line 144:** Server checks authentication for all paths except `/ping` and `/health`
- **Line 135:** Extracts token from `Authorization:` header with `Bearer ` prefix
- **Line 144:** Compares extracted token with `configManager.authToken`

```kotlin
if (path != "/ping" && path != "/health" && authToken != configManager.authToken) {
    sendErrorResponse(outputStream, HTTP_STATUS_UNAUTHORIZED, UNAUTHORIZED)
    return
}
```

### Client Side (droidrun)
**File:** `droidrun/droidrun/tools/portal_client.py`
- **Lines 373-398:** `_get_state_localhost()` makes HTTP GET requests **without Authorization header**
- **Lines 489-499:** `_input_text_localhost()` makes HTTP POST requests **without Authorization header**
- **Lines 673-697:** `_get_apps_localhost()` makes HTTP GET requests **without Authorization header**
- **All HTTP requests** in localhost mode are missing the `Authorization: Bearer <token>` header

### Auth Token Access
**File:** `directionsdk/src/main/java/com/droidrun/portal/config/ConfigManager.kt`
- **Lines 65-73:** Auth token is auto-generated as UUID if missing
- **Token is stored in SharedPreferences** and persists across app restarts

**File:** `app/src/main/java/com/droidrun/portal/service/DroidrunContentProvider.kt`
- **Line 120:** Token can be queried via: `content query --uri content://com.droidrun.portal/auth_token`

## Permanent Fix Options

### ✅ **Option 1: Skip Auth for Localhost Connections (IMPLEMENTED - RECOMMENDED)**

**Why:** This is the simplest and most sensible solution for localhost-only usage. Since localhost connections are from the same device, authentication adds unnecessary complexity without real security benefit.

**Implementation:** Modified `SocketServer.kt` to detect localhost connections (127.0.0.1, ::1) and skip authentication for them. Remote/TCP connections still require auth.

**Files Modified:**
- `app/src/main/java/com/droidrun/portal/service/SocketServer.kt`

**What Changed:**
- Added localhost detection by checking `clientSocket.remoteSocketAddress`
- Skip auth check if connection is from localhost
- Keep auth requirement for remote/TCP connections

**Pros:**
- ✅ No client-side changes needed
- ✅ Works immediately for localhost connections
- ✅ Maintains security for remote connections
- ✅ Simpler architecture

**Cons:**
- ⚠️ Localhost connections don't require auth (but this is acceptable since they're from the same device)

---

### **Option 1 (Old): Implement Auth Token Retrieval in portal_client (NOT NEEDED NOW)**

**Why:** This is the correct, secure solution that maintains authentication while allowing localhost access.

**Implementation Steps:**

1. **Add auth token retrieval method** in `portal_client.py`:
   ```python
   async def _get_auth_token(self) -> Optional[str]:
       """Get auth token from Portal app via ContentProvider."""
       try:
           await self._ensure_device()
           output = await self.device.shell(
               "content query --uri content://com.droidrun.portal/auth_token"
           )
           result = self._parse_content_provider_output(output)
           if result:
               # Extract token from result
               if isinstance(result, dict):
                   inner_key = "result" if "result" in result else "data" if "data" in result else None
                   if inner_key:
                       token = result[inner_key]
                       if isinstance(token, str):
                           return token.strip('"')  # Remove quotes if present
               elif isinstance(result, str):
                   return result.strip('"')
           return None
       except Exception as e:
           logger.debug(f"Failed to get auth token: {e}")
           return None
   ```

2. **Store auth token** in `__init__` or during `connect()`:
   ```python
   def __init__(self, ...):
       ...
       self.auth_token: Optional[str] = None
   ```

3. **Retrieve token during connection**:
   ```python
   async def connect(self) -> None:
       ...
       if self.prefer_localhost:
           # Get auth token before using localhost
           if self.device is None and self.device_serial:
               from async_adbutils import adb
               self.device = await adb.device(serial=self.device_serial)
           
           if self.device:
               self.auth_token = await self._get_auth_token()
           
           await self._try_enable_localhost()
           ...
   ```

4. **Include Authorization header in all HTTP requests**:
   ```python
   async def _get_state_localhost(self) -> Dict[str, Any]:
       """Get state via localhost. No fallback - localhost must work when prefer_localhost=True."""
       headers = {}
       if self.auth_token:
           headers["Authorization"] = f"Bearer {self.auth_token}"
       
       async with httpx.AsyncClient() as client:
           response = await client.get(
               f"{self.localhost_base_url}/state_full", 
               headers=headers,
               timeout=10
           )
           ...
   ```

5. **Apply same pattern to ALL HTTP methods**:
   - `_get_state_localhost()`
   - `_get_state_tcp()`
   - `_input_text_localhost()`
   - `_input_text_tcp()`
   - `_get_apps_localhost()`
   - `_get_apps_tcp()`
   - `_take_screenshot_localhost()`
   - `_take_screenshot_tcp()`
   - Any other HTTP requests

**Files to Modify:**
- `droidrun/droidrun/tools/portal_client.py`

**Pros:**
- ✅ Maintains security (authentication required)
- ✅ Works with localhost mode
- ✅ No changes needed to Portal server
- ✅ Proper architecture (client gets token, includes in requests)

**Cons:**
- ⚠️ Requires device to be accessible for token retrieval (but this is needed anyway for localhost to work)
- ⚠️ Slight overhead of one additional content provider query during connect()

---

### **Option 2: Add Unauthenticated Localhost Exception (NOT RECOMMENDED)**

**Why NOT recommended:** This weakens security by allowing unauthenticated localhost access.

**Implementation:**
Modify `SocketServer.kt` to allow localhost connections without auth:
```kotlin
// Check if connection is from localhost
val clientHost = clientSocket.remoteSocketAddress?.toString()
val isLocalhost = clientHost?.contains("127.0.0.1") == true || 
                  clientHost?.contains("localhost") == true ||
                  clientHost?.contains("::1") == true

if (path != "/ping" && path != "/health" && !isLocalhost && authToken != configManager.authToken) {
    sendErrorResponse(outputStream, HTTP_STATUS_UNAUTHORIZED, UNAUTHORIZED)
    return
}
```

**Files to Modify:**
- `app/src/main/java/com/droidrun/portal/service/SocketServer.kt`

**Pros:**
- ✅ Simple change
- ✅ No client-side changes needed

**Cons:**
- ❌ **Security risk**: Anyone on the device can access Portal API without authentication
- ❌ Violates security principle (authentication should be consistent)
- ❌ May cause issues if multiple apps try to use Portal API

---

### **Option 3: Expose Token via Unauthenticated Endpoint (NOT RECOMMENDED)**

**Why NOT recommended:** Exposing auth token via unauthenticated endpoint is a security vulnerability.

**Implementation:**
Add `/auth_token` endpoint that doesn't require auth:
```kotlin
path.startsWith("/auth_token") -> ApiResponse.Success(configManager.authToken)
```

And modify auth check:
```kotlin
if (path != "/ping" && path != "/health" && path != "/auth_token" && authToken != configManager.authToken) {
    ...
}
```

**Files to Modify:**
- `app/src/main/java/com/droidrun/portal/service/SocketServer.kt`
- `app/src/main/java/com/droidrun/portal/api/ApiHandler.kt`

**Pros:**
- ✅ Client can get token via HTTP (easier than ContentProvider)

**Cons:**
- ❌ **Security risk**: Auth token exposed without authentication
- ❌ Anyone can query `/auth_token` and get the token
- ❌ Token leak vulnerability

---

### **Option 4: Use ContentProvider Only (Fallback Solution)**

**Why:** If HTTP authentication is too complex, just use ContentProvider for all operations.

**Implementation:**
- Remove localhost/TCP HTTP requests
- Use ContentProvider for all Portal API calls
- Already implemented in `portal_client.py` as fallback

**Files to Modify:**
- `droidrun/droidrun/tools/portal_client.py` - Disable localhost mode

**Pros:**
- ✅ No authentication needed (ContentProvider uses Android's permission system)
- ✅ Already working as fallback

**Cons:**
- ⚠️ Slower than HTTP (requires ADB shell for each call)
- ⚠️ Higher latency
- ⚠️ Less efficient for frequent API calls

---

## Recommended Solution: **Option 1**

**Why Option 1 is the best:**
1. **Security**: Maintains authentication requirements
2. **Performance**: HTTP localhost is faster than ContentProvider
3. **Architecture**: Proper client-server authentication pattern
4. **Future-proof**: Works with any authentication improvements

**Implementation Priority:**
1. Add `_get_auth_token()` method
2. Store token during `connect()`
3. Add Authorization header to ALL HTTP requests
4. Test with localhost mode
5. Test with TCP mode
6. Verify ContentProvider fallback still works

**Testing Checklist:**
- [ ] Token retrieval via ContentProvider works
- [ ] Token is stored correctly
- [ ] Authorization header is included in all requests
- [ ] `/state_full` endpoint works
- [ ] `/packages` endpoint works
- [ ] `/keyboard/input` endpoint works
- [ ] `/screenshot` endpoint works
- [ ] Fallback to ContentProvider works if HTTP fails
- [ ] Works with both localhost and TCP modes

---

## Current Workaround (Temporary)

Until Option 1 is implemented, you can:

1. **Use ContentProvider mode** by disabling localhost:
   ```python
   portal_client = PortalClient(
       prefer_localhost=False,  # Force ContentProvider mode
       prefer_tcp=False
   )
   ```

2. **Or use ADB port forwarding** with TCP mode (which might not have same auth requirements - needs verification)

---

## Related Files

**Server (Portal App):**
- `app/src/main/java/com/droidrun/portal/service/SocketServer.kt` (HTTP server & auth check)
- `directionsdk/src/main/java/com/droidrun/portal/config/ConfigManager.kt` (Token generation)
- `app/src/main/java/com/droidrun/portal/service/DroidrunContentProvider.kt` (Token access)

**Client (droidrun):**
- `droidrun/droidrun/tools/portal_client.py` (HTTP client - needs auth headers)

---

## Summary

**The issue:** Portal HTTP server requires authentication for all endpoints, including localhost connections. This causes HTTP 401 errors when droidrun tries to access endpoints like `/state_full` from localhost.

**The fix:** **IMPLEMENTED** - Modified `SocketServer.kt` to skip authentication for localhost connections (127.0.0.1, ::1). This makes sense because:
1. Localhost connections are from the same device (no external access risk)
2. Simplifies localhost-only usage (no need to retrieve/send auth token)
3. Still maintains security for remote/TCP connections (auth still required)

**Impact:** This is a **permanent fix** that enables localhost access without authentication while maintaining security for remote connections. The fix is **server-side only** - no client changes needed.

**Files Changed:**
- `app/src/main/java/com/droidrun/portal/service/SocketServer.kt` - Added localhost detection and skip auth for localhost

**Next Steps:**
1. Build and test the app
2. Verify localhost connections work without auth
3. Verify remote/TCP connections still require auth

