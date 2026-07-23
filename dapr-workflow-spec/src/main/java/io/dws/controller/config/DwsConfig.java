package io.dws.controller.config;

import io.dws.controller.model.ImageCatalog;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/** Binds {@code dws.*} from application.yaml. */
@ConfigMapping(prefix = "dws")
public interface DwsConfig {

    /** Namespace the controller deploys managed stacks into. */
    @WithDefault("default")
    String namespace();

    Images images();

    Reconcile reconcile();

    /** Garbage-collection cadence for drained previous versions. */
    interface Reconcile {
        @WithDefault("30s")
        String every();
    }

    interface Images {
        String callHttp();

        String callOpenapi();

        String run();

        String orchestrator();
    }

    default ImageCatalog catalog() {
        return new ImageCatalog(
                images().callHttp(), images().callOpenapi(), images().run(), images().orchestrator());
    }
}
