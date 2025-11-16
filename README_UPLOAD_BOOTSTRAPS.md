# Upload Bootstraps to GitHub Releases

This Gradle task automatically uploads bootstrap ZIP files to GitHub Releases.

## Prerequisites

1. **GitHub Personal Access Token** with `repo` scope:
   - Go to: https://github.com/settings/tokens
   - Generate new token with `repo` permissions
   - Copy the token

2. **Bootstrap files** must exist in `../termux-packages/bootstraps/`:
   - `bootstrap-aarch64.zip`
   - `bootstrap-arm.zip`
   - `bootstrap-i686.zip`
   - `bootstrap-x86_64.zip`

## Usage

The task will automatically find your GitHub token from multiple sources (in order of priority):

### Option 1: Using `github.token` file (Recommended - Most Secure)

Create a file `termux-app/github.token` with your token:
```bash
cd termux-app
echo "your_github_token_here" > github.token
```

**Note:** This file is automatically gitignored and will never be committed to Git.

Then run:
```bash
./gradlew uploadBootstrapsToGitHub -PreleaseTag=v1.0.0
```

### Option 2: Using `local.properties` (Also Secure)

Add to `termux-app/local.properties` (this file is already gitignored):
```properties
github.token=your_github_token_here
```

Then run:
```bash
./gradlew uploadBootstrapsToGitHub -PreleaseTag=v1.0.0
```

### Option 3: Using Environment Variable

```bash
# Set GitHub token as environment variable
export GITHUB_TOKEN=your_github_token_here  # Linux/Mac
# OR
$env:GITHUB_TOKEN="your_github_token_here"  # Windows PowerShell

# Upload to GitHub Releases
cd termux-app
./gradlew uploadBootstrapsToGitHub -PreleaseTag=v1.0.0
```

### Option 4: Using Command Line Parameter (Less Secure)

```bash
cd termux-app
./gradlew uploadBootstrapsToGitHub -PreleaseTag=v1.0.0 -PgithubToken=your_token_here
```

**Note:** This method exposes the token in command history. Use Options 1-3 instead.

## Parameters

- `releaseTag` (required): Release tag name (e.g., `v1.0.0`, `latest`)
- `githubToken` (required): GitHub personal access token
- `github.repo.owner` (optional, default: `RiteshF7`): GitHub repository owner
- `github.repo.name` (optional, default: `termux-packages`): GitHub repository name

## What It Does

1. **Checks** if bootstrap files exist in `../termux-packages/bootstraps/`
2. **Creates** a new GitHub release with the specified tag (or uses existing release)
3. **Uploads** all 4 bootstrap ZIP files as release assets
4. **Prints** the release URL when complete

## Example Output

```
=== Uploading bootstraps to GitHub Releases ===
Repository: RiteshF7/termux-packages
Release Tag: v1.0.0
Files to upload: 4
Found: bootstrap-aarch64.zip (125 MB)
Found: bootstrap-arm.zip (118 MB)
Found: bootstrap-i686.zip (132 MB)
Found: bootstrap-x86_64.zip (135 MB)

Creating new release: v1.0.0
Release ID: 12345678

Uploading bootstrap-aarch64.zip...
✓ Uploaded bootstrap-aarch64.zip

Uploading bootstrap-arm.zip...
✓ Uploaded bootstrap-arm.zip

Uploading bootstrap-i686.zip...
✓ Uploaded bootstrap-i686.zip

Uploading bootstrap-x86_64.zip...
✓ Uploaded bootstrap-x86_64.zip

=== Upload Complete ===
Release URL: https://github.com/RiteshF7/termux-packages/releases/tag/v1.0.0
```

## Security

**Important:** Your GitHub token is sensitive and should never be committed to Git.

✅ **Safe methods (automatically gitignored):**
- `github.token` file
- `local.properties` file
- Environment variable `GITHUB_TOKEN`

❌ **Avoid:**
- Committing token to `gradle.properties` (it's tracked by Git)
- Passing token via command line (visible in history)
- Sharing token in screenshots or chat

## Troubleshooting

**Error: "GitHub token required"**
- Create `github.token` file with your token, OR
- Add `github.token=your_token` to `local.properties`, OR
- Set `GITHUB_TOKEN` environment variable, OR
- Pass `-PgithubToken=...` (less secure)

**Error: "Release tag required"**
- Pass `-PreleaseTag=v1.0.0` or set `github.release.tag` in `gradle.properties`

**Error: "Bootstrap file not found"**
- Run `build_bootstraps.bat` first to generate bootstrap files

**Error: "HTTP 401 Unauthorized"**
- Check that your GitHub token is valid and has `repo` scope

**Error: "HTTP 422 Unprocessable Entity"**
- Release tag might already exist with different assets
- Try a different tag or delete the existing release

## Integration with build_bootstraps.bat

After building bootstraps, you can automatically upload them:

```batch
REM Build bootstraps
call build_bootstraps.bat

REM Upload to GitHub Releases
cd ..\termux-app
gradlew uploadBootstrapsToGitHub -PreleaseTag=v1.0.0
```

