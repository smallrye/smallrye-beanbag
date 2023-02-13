package io.github.dmlloyd.maven;

import javax.inject.Named;

import org.apache.maven.wagon.Wagon;
import org.eclipse.aether.transport.wagon.WagonConfigurator;

/**
 * An empty configurator for Wagon.
 */
@Named
public final class BasicWagonConfigurator implements WagonConfigurator {
    @Override
    public void configure(Wagon wagon, Object configuration) throws Exception {
        // no operation
    }
}
