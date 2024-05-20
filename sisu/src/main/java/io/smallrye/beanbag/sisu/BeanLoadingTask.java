package io.smallrye.beanbag.sisu;

import java.io.IOException;

/**
 * Bean loading related task
 */
interface BeanLoadingTask {
    void run() throws IOException;
}
