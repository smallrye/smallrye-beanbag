package io.smallrye.beanbag;

import java.util.List;

/**
 * A definition for a scope.
 */
final class ScopeDefinition {
    private final List<BeanDefinition<?>> definitions;

    ScopeDefinition(final List<BeanDefinition<?>> definitions) {
        this.definitions = definitions;
    }

    List<BeanDefinition<?>> getBeanDefinitions() {
        return definitions;
    }
}
