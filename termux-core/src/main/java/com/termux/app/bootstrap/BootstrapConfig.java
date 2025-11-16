package com.termux.app.bootstrap;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration for bootstrap download URLs and checksums.
 * Maps architecture to GitHub raw URL and expected SHA-256 checksum.
 */
public class BootstrapConfig {
    // Bootstrap download URLs - choose one of the options below:
    
    // Option 1: GitHub Releases (recommended for large files)
    // Upload bootstrap ZIPs as release assets, then use URLs like:
    // "https://github.com/RiteshF7/termux-packages/releases/download/v1.0.0/bootstrap-{arch}.zip"
    
    // Option 2: Cloud Storage (AWS S3, Google Cloud Storage, Azure Blob, Cloudflare R2)
    // Example: "https://your-bucket.s3.amazonaws.com/bootstraps/bootstrap-{arch}.zip"
    // Example: "https://pub-xxxxx.r2.dev/bootstraps/bootstrap-{arch}.zip" (Cloudflare R2)
    
    // Option 3: GitHub Raw (only for smaller files < 100MB)
    // "https://raw.githubusercontent.com/RiteshF7/termux-packages/master/bootstraps/bootstrap-{arch}.zip"
    
    // Current configuration - UPDATE THIS with your actual hosting URL pattern
    // Use {arch} as placeholder which will be replaced with: aarch64, arm, i686, or x86_64
    // Here we point to GitHub release tag v1.0.0 where bootstraps are uploaded as assets.
    private static final String BOOTSTRAP_URL_PATTERN = "https://github.com/RiteshF7/termux-packages/releases/download/v1.0.0/bootstrap-{arch}.zip";
    
    // Optional: version or release tag of the bootstrap artifacts.
    // Set to "v1.0.0" to match the GitHub release tag where the bootstraps are hosted.
    // When this value is bumped in a new app build, the installer can detect that the cached
    // local bootstrap is outdated and force a fresh download.
    private static final String BOOTSTRAP_VERSION = "v1.0.0";
    
    // Alternative: If using Cloudflare R2 or other CDN, uncomment and update:
    // private static final String BOOTSTRAP_URL_PATTERN = "https://your-r2-bucket.r2.dev/bootstraps/bootstrap-{arch}.zip";
    
    // Map of architecture -> expected SHA-256 checksum
    // Update these checksums after uploading bootstraps
    private static final Map<String, String> CHECKSUMS = new HashMap<String, String>() {{
        put("aarch64", ""); // Will be populated after first bootstrap upload
        put("arm", "");
        put("i686", "");
        put("x86_64", "");
    }};

    /**
     * Gets the download URL for bootstrap for the given architecture.
     */
    public static String getBootstrapUrl(String arch) {
        return BOOTSTRAP_URL_PATTERN.replace("{arch}", arch);
    }

    /**
     * Gets the expected bootstrap version/release tag.
     * When this changes between app versions, cached local bootstraps can be invalidated.
     */
    public static String getBootstrapVersion() {
        return BOOTSTRAP_VERSION;
    }

    /**
     * Gets the expected SHA-256 checksum for the given architecture.
     * Returns empty string if checksum not yet configured (will skip verification).
     */
    public static String getExpectedChecksum(String arch) {
        return CHECKSUMS.getOrDefault(arch, "");
    }

    /**
     * Updates the expected checksum for an architecture (useful for runtime updates).
     */
    public static void setExpectedChecksum(String arch, String checksum) {
        CHECKSUMS.put(arch, checksum);
    }
}

