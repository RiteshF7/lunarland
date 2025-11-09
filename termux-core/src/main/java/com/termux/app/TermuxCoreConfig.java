package com.termux.app;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.core.BuildConfig;

/**
 * Configuration options supplied by the host application to initialize Termux core runtime.
 */
public final class TermuxCoreConfig {

    private final String mTermuxPackageVariant;

    private TermuxCoreConfig(@Nullable Builder builder) {
        if (builder == null || builder.mTermuxPackageVariant == null) {
            mTermuxPackageVariant = BuildConfig.TERMUX_PACKAGE_VARIANT;
        } else {
            mTermuxPackageVariant = builder.mTermuxPackageVariant;
        }
    }

    public static TermuxCoreConfig createDefault() {
        return new TermuxCoreConfig(null);
    }

    @NonNull
    public String getTermuxPackageVariant() {
        return mTermuxPackageVariant;
    }

    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String mTermuxPackageVariant;

        private Builder() {
        }

        @NonNull
        public Builder setTermuxPackageVariant(@NonNull String termuxPackageVariant) {
            mTermuxPackageVariant = termuxPackageVariant;
            return this;
        }

        @NonNull
        public TermuxCoreConfig build() {
            return new TermuxCoreConfig(this);
        }
    }
}
