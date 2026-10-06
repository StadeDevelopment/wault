# Keep annotations and signatures that reflection-based libraries rely on.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions, EnclosingMethod

# kotlinx.serialization generates companion serializers that R8 cannot see are used.
# Without these the vault payloads, sync frames and pairing offers fail to decode.
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class net.wault.**$$serializer { *; }
-keepclassmembers class net.wault.** { *** Companion; }
-keepclasseswithmembers class net.wault.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers @kotlinx.serialization.Serializable class net.wault.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# Sealed hierarchies serialised polymorphically (ItemContent, SyncMessage, PairingMessage).
-keep class net.wault.item.ItemContent { *; }
-keep class net.wault.item.ItemContent$* { *; }
-keep class net.wault.sync.SyncMessage { *; }
-keep class net.wault.sync.SyncMessage$* { *; }
-keep class net.wault.sync.PairingMessage { *; }
-keep class net.wault.sync.PairingMessage$* { *; }

# BouncyCastle resolves algorithms by name at runtime.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# Ktor networking used by the LAN and Tor transports.
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }
-dontwarn io.ktor.**

-keep class app.cash.sqldelight.** { *; }
-dontwarn app.cash.sqldelight.**

-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn kotlinx.coroutines.**

# Entry points the framework instantiates by name.
-keep class net.wault.MainActivity { *; }
-keep class net.wault.WaultApplication { *; }
-keep class net.wault.autofill.WaultAutofillService { *; }
-keep class net.wault.autofill.AutofillUnlockActivity { *; }
-keep class net.wault.autofill.WaultAccessibilityService { *; }
-keep class net.wault.autofill.AccessibilityFillActivity { *; }
