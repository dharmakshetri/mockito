package org.mockito.internal.creation.bytebuddy;

import static org.mockito.internal.util.StringJoiner.join;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.InternalMockHandler;
import org.mockito.internal.creation.instance.Instantiator;
import org.mockito.internal.creation.instance.InstantiatorProvider;
import org.mockito.invocation.MockHandler;
import org.mockito.mock.MockCreationSettings;
import org.mockito.mock.SerializableMode;
import org.mockito.plugins.MockMaker;

public class ByteBuddyMockMaker implements MockMaker {

    private final CachingMockBytecodeGenerator cachingMockBytecodeGenerator;

    public ByteBuddyMockMaker() {
        cachingMockBytecodeGenerator = new CachingMockBytecodeGenerator();
    }

    public <T> T createMock(MockCreationSettings<T> settings, MockHandler handler) {
        if (settings.getSerializableMode() == SerializableMode.ACROSS_CLASSLOADERS) {
            throw new MockitoException("Serialization across classloaders not yet supported with ByteBuddyMockMaker");
        }
        Class<? extends T> mockedProxyType = cachingMockBytecodeGenerator.get(
                settings.getTypeToMock(),
                settings.getExtraInterfaces()
        );
        Instantiator instantiator = new InstantiatorProvider().getInstantiator(settings);
        T mockInstance = null;
        try {
            mockInstance = instantiator.newInstance(mockedProxyType);
            MockMethodInterceptor.MockAccess mockAccess = (MockMethodInterceptor.MockAccess) mockInstance;
            mockAccess.setMockitoInterceptor(new MockMethodInterceptor(asInternalMockHandler(handler), settings));

            return ensureMockIsAssignableToMockedType(settings, mockInstance);
        } catch (ClassCastException cce) {
            throw new MockitoException(join(
                    "ClassCastException occurred while creating the mockito mock :",
                    "  class to mock : " + describeClass(mockedProxyType),
                    "  created class : " + describeClass(settings.getTypeToMock()),
                    "  proxy instance class : " + describeClass(mockInstance),
                    "  instance creation by : " + instantiator.getClass().getSimpleName(),
                    "",
                    "You might experience classloading issues, please ask the mockito mailing-list.",
                    ""
            ),cce);
        } catch (org.mockito.internal.creation.instance.InstantiationException e) {
            throw new MockitoException("Unable to create mock instance of type '" + mockedProxyType.getSuperclass().getSimpleName() + "'", e);
        }
    }

    private <T> T ensureMockIsAssignableToMockedType(MockCreationSettings<T> settings, T mock) {
        // Force explicit cast to mocked type here, instead of
        // relying on the JVM to implicitly cast on the client call site.
        // This allows us to catch the ClassCastException earlier
        Class<T> typeToMock = settings.getTypeToMock();
        return typeToMock.cast(mock);
    }

    private static String describeClass(Class type) {
        return type == null ? "null" : "'" + type.getCanonicalName() + "', loaded by classloader : '" + type.getClassLoader() + "'";
    }

    private static String describeClass(Object instance) {
        return instance == null ? "null" : describeClass(instance.getClass());
    }

    public MockHandler getHandler(Object mock) {
        if (!(mock instanceof MockMethodInterceptor.MockAccess)) {
            return null;
        }
        return ((MockMethodInterceptor.MockAccess) mock).getMockitoInterceptor().getMockHandler();
    }

    public void resetMock(Object mock, MockHandler newHandler, MockCreationSettings settings) {
        ((MockMethodInterceptor.MockAccess) mock).setMockitoInterceptor(
                new MockMethodInterceptor(asInternalMockHandler(newHandler), settings)
        );
    }

    private static InternalMockHandler asInternalMockHandler(MockHandler handler) {
        if (!(handler instanceof InternalMockHandler)) {
            throw new MockitoException(join(
                    "At the moment you cannot provide own implementations of MockHandler.",
                    "Please see the javadocs for the MockMaker interface.",
                    ""
            ));
        }
        return (InternalMockHandler) handler;
    }
}
