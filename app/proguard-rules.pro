# ClearGuard currently uses no reflection-heavy third-party SDKs.

# OkHttp ships its own consumer rules; these silence warnings for the optional
# security providers it probes for at runtime but which are not bundled.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
