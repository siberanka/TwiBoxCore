# Third-party boundaries

TwiBoxCore does not copy, modify, shade, or redistribute ItemEdit or ItemTag code or binaries.

- [ItemEdit](https://github.com/emanondev/ItemEdit), by emanondev, is distributed separately under GPL-3.0.
- [ItemTag](https://github.com/emanondev/ItemTag), by emanondev, is distributed separately under GPL-3.0.

The optional integration is limited to recognizing existing namespaced persistent-data values written to items. Server owners must download and maintain those plugins independently if they need their editing commands or runtime features.

TwiBoxCore also does not bundle or modify PlayerKits2, Shopkeepers, ExcellentCrates, or UnlimitedNameTags. UnlimitedNameTags was verified as an original third-party component and is intentionally excluded from the merge. These names appear only where load ordering or migration documentation benefits from identifying the surrounding integration.

The packet-NPC shop module compiles against, but does not bundle or redistribute:

- [Shopkeepers](https://github.com/Shopkeepers/Shopkeepers), maintained by blablubbabc and contributors, under GPL-3.0;
- [FancyNpcs](https://github.com/FancyInnovations/FancyPlugins), maintained by FancyInnovations contributors, under MIT.

Their classes remain server-provided dependencies. TwiBoxCore's own implementation, including its custom object lifecycle and guarded editor bridge, is authored for this project.
