/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2020.
 */
package dev.galasa.zosunixcommand.ssh.manager.internal;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import dev.galasa.ManagerException;
import dev.galasa.framework.spi.ConfigurationPropertyStoreException;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.framework.spi.IManager;
import dev.galasa.ipnetwork.ICommandShell;
import dev.galasa.ipnetwork.IIpHost;
import dev.galasa.ipnetwork.internal.IpNetworkManagerImpl;
import dev.galasa.zos.IZosImage;
import dev.galasa.zos.internal.ZosManagerImpl;
import dev.galasa.zosunixcommand.ssh.manager.internal.properties.ZosUNIXCommandSshPropertiesSingleton;
import dev.galasa.zosunixcommand.IZosUNIXCommand;
import dev.galasa.zosunixcommand.ZosUNIXCommandException;
import dev.galasa.zosunixcommand.ZosUNIXCommandManagerException;

@RunWith(PowerMockRunner.class)
public class TestZosUNIXCommandManagerImpl {
    
    private ZosUNIXCommandManagerImpl zosUnixCommandManager;
    
    private ZosUNIXCommandManagerImpl zosUnixCommandManagerSpy;
    
    private ZosUNIXCommandSshPropertiesSingleton zosUnixCommandSshPropertiesSingleton;

    private List<IManager> allManagers;
    
    private List<IManager> activeManagers;
    
    @Mock
    private IFramework frameworkMock;
    
    @Mock
    public IManager managerMock;
    
    @Mock
    private ZosManagerImpl zosManagerMock;
    
    @Mock
    private IpNetworkManagerImpl ipNetworkManagerMock;

    @Mock
    private IZosImage zosImageMock;
    
    @Mock
    private IIpHost ipHostMock;
    
    @Mock
    private ICommandShell commandShellMock;

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Before
    public void setup() throws Exception {        
        ZosUNIXCommandManagerImpl.setZosManager(zosManagerMock);
        Mockito.when(zosManagerMock.getImageForTag(Mockito.any())).thenReturn(zosImageMock);
        ZosUNIXCommandManagerImpl.setIpNetworkManager(ipNetworkManagerMock);
        zosUnixCommandSshPropertiesSingleton = new ZosUNIXCommandSshPropertiesSingleton();
        zosUnixCommandSshPropertiesSingleton.activate();
        
        Mockito.when(zosImageMock.getImageID()).thenReturn("image");        
        Mockito.when(zosImageMock.getIpHost()).thenReturn(ipHostMock);
        
        zosUnixCommandManager = new ZosUNIXCommandManagerImpl();
        zosUnixCommandManagerSpy = Mockito.spy(zosUnixCommandManager);
        Mockito.when(zosUnixCommandManagerSpy.getFramework()).thenReturn(frameworkMock);
        
        Mockito.when(ipNetworkManagerMock.getCommandShell(Mockito.any(), Mockito.any())).thenReturn(commandShellMock);
        Mockito.doNothing().when(commandShellMock).reportResultStrings(Mockito.anyBoolean());
        
        allManagers = new ArrayList<>();
        activeManagers = new ArrayList<>();
    }
    
    @Test
    public void testInitialise() throws ManagerException {
        allManagers.add(managerMock);
        zosUnixCommandManager.initialise(frameworkMock, allManagers, activeManagers, TestZosUNIXCommandManagerImpl.class);
        Assert.assertEquals("Error in initialise() method", zosUnixCommandManagerSpy.getFramework(), frameworkMock);
    }
    
    @Test
    public void testInitialise1() throws ManagerException {
        Mockito.doNothing().when(zosUnixCommandManagerSpy).youAreRequired(Mockito.any(), Mockito.any());
        zosUnixCommandManagerSpy.initialise(frameworkMock, allManagers, activeManagers, DummyTestClass.class);
        Assert.assertEquals("Error in initialise() method", zosUnixCommandManagerSpy.getFramework(), frameworkMock);
    }

    @Test
    public void testInitialiseException() throws ConfigurationPropertyStoreException, ManagerException {
        Mockito.when(frameworkMock.getConfigurationPropertyService(Mockito.any())).thenThrow(new ConfigurationPropertyStoreException("exception"));
        exceptionRule.expect(ZosUNIXCommandManagerException.class);
        exceptionRule.expectMessage("Unable to request framework services");
        zosUnixCommandManagerSpy.initialise(frameworkMock, allManagers, activeManagers, DummyTestClass.class);
    }
    
    @Test
    public void testProvisionGenerate() throws Exception {
        PowerMockito.doNothing().when(zosUnixCommandManagerSpy, "generateAnnotatedFields", Mockito.any());
        zosUnixCommandManagerSpy.provisionGenerate();
        PowerMockito.verifyPrivate(zosUnixCommandManagerSpy, Mockito.times(1)).invoke("generateAnnotatedFields", Mockito.any());
    }
    
    @Test
    public void testYouAreRequired() throws Exception {
        allManagers.add(zosManagerMock);
        allManagers.add(ipNetworkManagerMock);
        zosUnixCommandManagerSpy.youAreRequired(allManagers, activeManagers);
        PowerMockito.verifyPrivate(zosUnixCommandManagerSpy, Mockito.times(2)).invoke("addDependentManager", Mockito.any(), Mockito.any(), Mockito.any());
        
        Mockito.clearInvocations(zosUnixCommandManagerSpy);
        zosUnixCommandManagerSpy.youAreRequired(allManagers, activeManagers);
        PowerMockito.verifyPrivate(zosUnixCommandManagerSpy, Mockito.times(0)).invoke("addDependentManager", Mockito.any(), Mockito.any(), Mockito.any());
    }
    
    @Test
    public void testYouAreRequiredException1() throws ManagerException {
        exceptionRule.expect(ManagerException.class);
        exceptionRule.expectMessage("The zOS Manager is not available");
        zosUnixCommandManagerSpy.youAreRequired(allManagers, activeManagers);
    }
    
    @Test
    public void testYouAreRequiredException2() throws ManagerException {
        allManagers.add(zosManagerMock);
        exceptionRule.expect(ManagerException.class);
        exceptionRule.expectMessage("The IP Network Manager is not available");
        zosUnixCommandManagerSpy.youAreRequired(allManagers, activeManagers);
    }
    
    @Test
    public void testAreYouProvisionalDependentOn() {
        Assert.assertTrue("Should be dependent on IZosManagerSpi" , zosUnixCommandManager.areYouProvisionalDependentOn(zosManagerMock));
        Assert.assertTrue("Should be dependent on IIpNetworkManagerSpi" , zosUnixCommandManager.areYouProvisionalDependentOn(ipNetworkManagerMock));
        Assert.assertFalse("Should not be dependent on IManager" , zosUnixCommandManager.areYouProvisionalDependentOn(managerMock));
    }
    
    @Test
    public void testGenerateZosUNIX() throws NoSuchMethodException, SecurityException, ManagerException, NoSuchFieldException {
        List<Annotation> annotations = new ArrayList<>();
        Annotation annotation = DummyTestClass.class.getAnnotation(dev.galasa.zosunixcommand.ZosUNIXCommand.class);
        annotations.add(annotation);
        
        Object zosUNIXImplObject = zosUnixCommandManager.generateZosUNIXCommand(DummyTestClass.class.getDeclaredField("zosUnixCommand"), annotations);
        Assert.assertTrue("Error in generateZosUNIX() method", zosUNIXImplObject instanceof ZosUNIXCommandImpl);
        
        HashMap<String, ZosUNIXCommandImpl> taggedZosUNIXCommands = new HashMap<>();
        ZosUNIXCommandImpl zosUNIXCommandImpl = Mockito.mock(ZosUNIXCommandImpl.class);
        taggedZosUNIXCommands.put("tag", zosUNIXCommandImpl);
        Whitebox.setInternalState(zosUnixCommandManagerSpy, "taggedZosUNIXCommands", taggedZosUNIXCommands);
        
        zosUNIXImplObject = zosUnixCommandManagerSpy.generateZosUNIXCommand(DummyTestClass.class.getDeclaredField("zosUnixCommand"), annotations);
        Assert.assertEquals("generateZosUNIX() should retrn the supplied instance of ZosUNIXCommandImpl", zosUNIXCommandImpl, zosUNIXImplObject);
    }
    
    @Test
    public void testGetZosUNIX() throws ZosUNIXCommandManagerException {
        IZosUNIXCommand zosUnix = zosUnixCommandManagerSpy.getZosUNIXCommand(zosImageMock);
        Assert.assertNotNull("getZosUNIX() should not be null", zosUnix);
        IZosUNIXCommand zosUnix2 = zosUnixCommandManagerSpy.getZosUNIXCommand(zosImageMock);
        Assert.assertEquals("getZosUNIX() should return the existing IZosUNIXCommand instance", zosUnix, zosUnix2);
    }
    
    class DummyTestClass {
        @dev.galasa.zosunixcommand.ZosUNIXCommand(imageTag="tag")
        public dev.galasa.zosunixcommand.IZosUNIXCommand zosUnixCommand;
        @dev.galasa.Test
        public void dummyTestMethod() throws ZosUNIXCommandException {
            zosUnixCommand.issueCommand("command");
        }
    }
}