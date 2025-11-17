# Understanding compileSdkVersion vs targetSdkVersion

## The Key Concept

**APIs are compiled into your code, but they must exist on the device at runtime.**

## Example Scenario

### Scenario 1: Using an API that exists in both compile SDK and device
```
compileSdkVersion = 31
targetSdkVersion = 28
Device running = Android 10 (API 29)

You use: Environment.getExternalStorageDirectory()
- ✅ Compiles fine (exists in SDK 31)
- ✅ Works at runtime (exists on API 29 device)
```

### Scenario 2: Using an API removed from SDK
```
compileSdkVersion = 33
targetSdkVersion = 28

You try to use: settings.setAppCacheEnabled(false)
- ❌ Compile error (removed from SDK 33)
- The code never compiles, so it never runs
```

### Scenario 3: Using a newer API on older device
```
compileSdkVersion = 31
targetSdkVersion = 28
Device running = Android 8 (API 26)

You use: Some API introduced in API 30
- ✅ Compiles fine (exists in SDK 31)
- ❌ Runtime crash (doesn't exist on API 26 device)
- Solution: Check Build.VERSION.SDK_INT before using
```

## What Actually Happens

1. **Compilation**: Your Java/Kotlin code is compiled to bytecode/DEX
   - The bytecode contains references to Android framework classes/methods
   - These references are resolved against your compileSdkVersion

2. **Runtime**: When your app runs on a device:
   - The Android framework on the device provides the actual implementations
   - If an API doesn't exist on the device, you get a runtime error (NoSuchMethodError, etc.)

## Why targetSdkVersion Matters

`targetSdkVersion` tells Android: "I've tested my app with this API level's behavior"

- It affects **compatibility behaviors** Android applies
- Example: With targetSdkVersion 30+, Android enforces scoped storage
- With targetSdkVersion 28, Android allows legacy storage access

## The Confusion

**Question**: "If I compile with API 31, how can APIs from API 28 work at runtime?"

**Answer**: 
- SDK 31 **includes** all APIs from previous versions (backward compatible)
- When you compile with SDK 31, you can use APIs from API 1, 2, 3... up to 31
- At runtime, if the device is running API 28, APIs from API 28 and below exist on the device
- So your compiled code (using API 28 APIs) works fine on an API 28 device

## The Real Issue with Removed APIs

When Google **removes** an API (not just deprecates):
- It's removed from the SDK entirely
- You can't compile code that uses it with newer compileSdkVersion
- Even if you compiled with an older SDK, it might crash at runtime on newer devices

Example: `setAppCacheEnabled()` was removed in API 33
- Can't compile with compileSdkVersion 33 if you use it
- If you compiled with SDK 31, it might work on API 31 devices but crash on API 33 devices


