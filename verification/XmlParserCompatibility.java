package com.pingpaisa.app;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import org.kxml2.io.KXmlParser;

import static net.bytebuddy.matcher.ElementMatchers.named;

/** Android's KXml supports this disabled feature; the desktop test dependency does not. */
public final class XmlParserCompatibility {
    public static void install() throws Exception {
        // Android's compilation boot classpath omits java.instrument, although
        // the desktop test JVM includes it. Avoid exposing that return type here.
        ByteBuddyAgent.class.getMethod("install").invoke(null);
        new ByteBuddy().redefine(KXmlParser.class)
            .visit(Advice.to(DisabledDocdecl.class).on(named("setFeature")))
            .make().load(KXmlParser.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());
    }

    public static final class DisabledDocdecl {
        @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
        public static boolean skip(@Advice.Argument(0) String name, @Advice.Argument(1) boolean enabled) {
            return !enabled && "http://xmlpull.org/v1/doc/features.html#process-docdecl".equals(name);
        }
    }
}
