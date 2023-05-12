package io.smallrye.beanbag.maven;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.composition.CycleDetectedInComponentGraphException;
import org.codehaus.plexus.component.repository.ComponentDescriptor;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.codehaus.plexus.context.Context;

import io.smallrye.beanbag.BeanBag;
import io.smallrye.beanbag.DependencyFilter;

@Named()
@Priority(-100)
@Singleton
class PlexusContainerImpl implements PlexusContainer {
    private final BeanBag bb;

    @Inject
    public PlexusContainerImpl(final BeanBag bb) {
        this.bb = bb;
    }

    public Context getContext() {
        throw uns();
    }

    public Object lookup(final String role) throws ComponentLookupException {
        try {
            return bb.requireBean(Class.forName(role));
        } catch (Exception e) {
            throw new ComponentLookupException(e, role, null);
        }
    }

    public Object lookup(final String role, final String hint) throws ComponentLookupException {
        try {
            return bb.requireBean(Class.forName(role), hint);
        } catch (Exception e) {
            throw new ComponentLookupException(e, role, hint);
        }
    }

    public <T> T lookup(final Class<T> role) throws ComponentLookupException {
        try {
            return bb.requireBean(role);
        } catch (Exception e) {
            throw new ComponentLookupException(e, role.getName(), null);
        }
    }

    public <T> T lookup(final Class<T> role, final String hint) throws ComponentLookupException {
        try {
            return bb.requireBean(role, hint);
        } catch (Exception e) {
            throw new ComponentLookupException(e, role.getName(), hint);
        }
    }

    public <T> T lookup(final Class<T> type, final String role, final String hint) throws ComponentLookupException {
        try {
            return type.cast(lookup(role, hint));
        } catch (Exception e) {
            throw new ComponentLookupException(e, role, hint);
        }
    }

    public List<Object> lookupList(final String role) throws ComponentLookupException {
        try {
            return List.copyOf(bb.getAllBeans(Class.forName(role)));
        } catch (Exception e) {
            throw new ComponentLookupException(e, role, null);
        }
    }

    public <T> List<T> lookupList(final Class<T> role) throws ComponentLookupException {
        try {
            return Collections.checkedList(bb.getAllBeans(role), role);
        } catch (Exception e) {
            throw new ComponentLookupException(e, role.getName(), null);
        }
    }

    public Map<String, Object> lookupMap(final String role) throws ComponentLookupException {
        try {
            return Map.copyOf(bb.newScope().getAllBeansWithNames(Class.forName(role), DependencyFilter.ACCEPT));
        } catch (Exception e) {
            throw new ComponentLookupException(e, role, null);
        }
    }

    public <T> Map<String, T> lookupMap(final Class<T> role) throws ComponentLookupException {
        try {
            return Map.copyOf(bb.newScope().getAllBeansWithNames(role, DependencyFilter.ACCEPT));
        } catch (Exception e) {
            throw new ComponentLookupException(e, role.getName(), null);
        }
    }

    public boolean hasComponent(final String role) {
        try {
            return bb.getOptionalBean(Class.forName(role)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasComponent(final String role, final String hint) {
        try {
            return bb.getOptionalBean(Class.forName(role), hint) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasComponent(final Class<?> role) {
        try {
            return bb.getOptionalBean(role) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasComponent(final Class<?> role, final String hint) {
        try {
            return bb.getOptionalBean(role, hint) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean hasComponent(final Class<?> type, final String role, final String hint) {
        try {
            return type.cast(bb.getOptionalBean(Class.forName(role), hint)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void addComponent(final Object component, final String role) {
        throw uns();
    }

    public <T> void addComponent(final T component, final Class<?> role, final String hint) {
        throw uns();
    }

    public <T> void addComponentDescriptor(final ComponentDescriptor<T> descriptor)
            throws CycleDetectedInComponentGraphException {
        throw uns();
    }

    public ComponentDescriptor<?> getComponentDescriptor(final String role, final String hint) {
        throw uns();
    }

    public <T> ComponentDescriptor<T> getComponentDescriptor(final Class<T> type, final String role, final String hint) {
        throw uns();
    }

    public List<ComponentDescriptor<?>> getComponentDescriptorList(final String role) {
        throw uns();
    }

    public <T> List<ComponentDescriptor<T>> getComponentDescriptorList(final Class<T> type, final String role) {
        throw uns();
    }

    public Map<String, ComponentDescriptor<?>> getComponentDescriptorMap(final String role) {
        throw uns();
    }

    public <T> Map<String, ComponentDescriptor<T>> getComponentDescriptorMap(final Class<T> type, final String role) {
        throw uns();
    }

    public List<ComponentDescriptor<?>> discoverComponents(final ClassRealm classRealm) throws PlexusConfigurationException {
        throw uns();
    }

    public ClassRealm getContainerRealm() {
        throw uns();
    }

    public ClassRealm setLookupRealm(final ClassRealm realm) {
        throw uns();
    }

    public ClassRealm getLookupRealm() {
        throw uns();
    }

    public ClassRealm createChildRealm(final String id) {
        throw uns();
    }

    public void release(final Object component) {
    }

    public void releaseAll(final Map<String, ?> components) {
    }

    public void releaseAll(final List<?> components) {
    }

    public void dispose() {
    }

    private static UnsupportedOperationException uns() {
        return new UnsupportedOperationException();
    }
}
