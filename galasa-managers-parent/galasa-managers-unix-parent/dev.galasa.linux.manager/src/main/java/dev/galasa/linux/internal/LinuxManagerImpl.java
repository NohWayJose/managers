/*
 * Licensed Materials - Property of IBM
 * 
 * (c) Copyright IBM Corp. 2019.
 */
package dev.galasa.linux.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osgi.service.component.annotations.Component;

import dev.galasa.ManagerException;
import dev.galasa.ipnetwork.IIpHost;
import dev.galasa.ipnetwork.spi.IIpNetworkManagerSpi;
import dev.galasa.framework.spi.AbstractManager;
import dev.galasa.framework.spi.AnnotatedField;
import dev.galasa.framework.spi.GenerateAnnotatedField;
import dev.galasa.framework.spi.IConfigurationPropertyStoreService;
import dev.galasa.framework.spi.IDynamicStatusStoreService;
import dev.galasa.framework.spi.IFramework;
import dev.galasa.framework.spi.IManager;
import dev.galasa.framework.spi.ResourceUnavailableException;
import dev.galasa.linux.ILinuxImage;
import dev.galasa.linux.LinuxImage;
import dev.galasa.linux.LinuxIpHost;
import dev.galasa.linux.LinuxManagerException;
import dev.galasa.linux.OperatingSystem;
import dev.galasa.linux.internal.properties.LinuxPropertiesSingleton;
import dev.galasa.linux.spi.ILinuxManagerSpi;
import dev.galasa.linux.spi.ILinuxProvisioner;

@Component(service = { IManager.class })
public class LinuxManagerImpl extends AbstractManager implements ILinuxManagerSpi {
    protected final static String              NAMESPACE    = "linux";

    private final static Log                   logger       = LogFactory.getLog(LinuxManagerImpl.class);

    private LinuxProperties                    linuxProperties;
    private IConfigurationPropertyStoreService cps;
    private IDynamicStatusStoreService         dss;
    private IIpNetworkManagerSpi               ipManager;

    private final ArrayList<ILinuxProvisioner> provisioners = new ArrayList<>();

    private final HashMap<String, ILinuxImage> taggedImages = new HashMap<>();

    /*
     * By default we need to load any managers that could provision linux images for
     * us, eg OpenStack
     */
    @Override
    public List<String> extraBundles(@NotNull IFramework framework) throws LinuxManagerException {
        this.linuxProperties = new LinuxProperties(framework);
        return this.linuxProperties.getExtraBundles();
    }

    @Override
    public void registerProvisioner(ILinuxProvisioner provisioner) {
        if (!provisioners.contains(provisioner)) {
            this.provisioners.add(provisioner);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * dev.galasa.framework.spi.AbstractManager#initialise(dev.galasa.framework.spi.
     * IFramework, java.util.List, java.util.List, java.lang.Class)
     */
    @Override
    public void initialise(@NotNull IFramework framework, @NotNull List<IManager> allManagers,
            @NotNull List<IManager> activeManagers, @NotNull Class<?> testClass) throws ManagerException {
        super.initialise(framework, allManagers, activeManagers, testClass);

        // *** Check to see if any of our annotations are present in the test class
        // *** If there is, we need to activate
        List<AnnotatedField> ourFields = findAnnotatedFields(LinuxManagerField.class);
        if (!ourFields.isEmpty()) {
            youAreRequired(allManagers, activeManagers);
        }

        try {
            this.dss = framework.getDynamicStatusStoreService(NAMESPACE);
            LinuxPropertiesSingleton.setCps(framework.getConfigurationPropertyService(NAMESPACE));
            this.cps = LinuxPropertiesSingleton.cps();
        } catch (Exception e) {
            throw new LinuxManagerException("Unable to request framework services", e);
        }

        // *** Ensure our DSE Provisioner is at the top of the list
        this.provisioners.add(new LinuxDSEProvisioner(this));
    }

    @Override
    public void youAreRequired(@NotNull List<IManager> allManagers, @NotNull List<IManager> activeManagers)
            throws ManagerException {
        if (activeManagers.contains(this)) {
            return;
        }

        activeManagers.add(this);
        ipManager = addDependentManager(allManagers, activeManagers, IIpNetworkManagerSpi.class);
        if (ipManager == null) {
            throw new LinuxManagerException("The IP Network Manager is not available");
        }
    }

    @Override
    public boolean areYouProvisionalDependentOn(@NotNull IManager otherManager) {
        for (ILinuxProvisioner provisioner : provisioners) {
            if (provisioner instanceof LinuxDSEProvisioner) {
                continue;
            }

            if (otherManager == provisioner) {
                return true;
            }
        }
        return super.areYouProvisionalDependentOn(otherManager);
    }

    /*
     * (non-Javadoc)
     * 
     * @see dev.galasa.framework.spi.AbstractManager#provisionGenerate()
     */
    @Override
    public void provisionGenerate() throws ManagerException, ResourceUnavailableException {
        // *** First add our default provisioning agent to the end
        this.provisioners.add(new LinuxDefaultProvisioner());

        // *** Get all our annotated fields
        List<AnnotatedField> annotatedFields = findAnnotatedFields(LinuxManagerField.class);

        // *** First, locate all the ILinuxImage fields
        // *** And then generate them
        Iterator<AnnotatedField> annotatedFieldIterator = annotatedFields.iterator();
        while (annotatedFieldIterator.hasNext()) {
            AnnotatedField annotatedField = annotatedFieldIterator.next();
            final Field field = annotatedField.getField();
            final List<Annotation> annotations = annotatedField.getAnnotations();

            if (field.getType() == ILinuxImage.class) {
                LinuxImage annotationLinuxImage = field.getAnnotation(LinuxImage.class);
                if (annotationLinuxImage != null) {
                    ILinuxImage linuxImage = generateLinuxImage(field, annotations);
                    registerAnnotatedField(field, linuxImage);
                }
            }
        }

        // *** Auto generate the remaining fields
        generateAnnotatedFields(LinuxManagerField.class);
    }

    private ILinuxImage generateLinuxImage(Field field, List<Annotation> annotations)
            throws ResourceUnavailableException {
        LinuxImage annotationLinuxImage = field.getAnnotation(LinuxImage.class);

        // *** Default the tag to primary
        String tag = defaultString(annotationLinuxImage.imageTag(), "PRIMARY").toUpperCase();

        // *** Have we already generated this tag
        if (taggedImages.containsKey(tag)) {
            return taggedImages.get(tag);
        }

        // *** Need a new linux image, lets ask the provisioners for one
        OperatingSystem operatingSystem = annotationLinuxImage.operatingSystem();
        if (operatingSystem == null) {
            operatingSystem = OperatingSystem.any;
        }

        String[] capabilities = annotationLinuxImage.capabilities();
        if (capabilities == null) {
            capabilities = new String[0];
        }
        List<String> capabilitiesTrimmed = AbstractManager.trim(capabilities);

        ILinuxImage image = null;
        for (ILinuxProvisioner provisioner : this.provisioners) {
            try {
                image = provisioner.provision(tag, operatingSystem, capabilitiesTrimmed);
            } catch (ManagerException e) {
                // *** There must be an error somewhere, put the run into resource wait
                throw new ResourceUnavailableException("Error during resource generate", e);
            }
            if (image != null) {
                break;
            }
        }

        if (image == null) {
            throw new ResourceUnavailableException(
                    "There are no linux images available for provisioning the @LinuxImage tagged " + tag);
        }

        taggedImages.put(tag, image);

        return image;
    }

    @Override
    public void provisionBuild() throws ManagerException, ResourceUnavailableException {
        super.provisionBuild();

        // *** We need to find all out IIpHosts for the images that we build and have an
        // annotation for

        // *** Get all our annotated fields
        List<AnnotatedField> annotatedFields = findAnnotatedFields(LinuxManagerField.class);

        // *** First, locate all the ILinuxImage fields
        // *** And then generate them
        Iterator<AnnotatedField> annotatedFieldIterator = annotatedFields.iterator();
        while (annotatedFieldIterator.hasNext()) {
            AnnotatedField annotatedField = annotatedFieldIterator.next();
            final Field field = annotatedField.getField();
            final List<Annotation> annotations = annotatedField.getAnnotations();

            if (field.getType() == IIpHost.class) {
                IIpHost iIpHost = generateIpHost(field, annotations);
                registerAnnotatedField(field, iIpHost);
            }
        }

    }

    public IIpHost generateIpHost(Field field, List<Annotation> annotations) throws LinuxManagerException {
        LinuxIpHost annotationHost = field.getAnnotation(LinuxIpHost.class);

        // *** Default the tag to primary
        String tag = defaultString(annotationHost.imageTag(), "primary");

        // *** Ensure we have this tagged host
        ILinuxImage image = taggedImages.get(tag);
        if (image == null) {
            throw new LinuxManagerException("Unable to provision an IP Host for field " + field.getName()
                    + " as no @LinuxImage for the tag '" + tag + "' was present");
        }

        return image.getIpHost();
    }

    protected IConfigurationPropertyStoreService getCps() {
        return this.cps;
    }

    protected IDynamicStatusStoreService getDss() {
        return this.dss;
    }

    protected IIpNetworkManagerSpi getIpNetworkManager() {
        return this.ipManager;
    }

}
