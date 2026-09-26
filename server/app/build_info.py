"""Build identity baked into container images; source runs remain explicit."""

import os


BUILD_VERSION = os.environ.get("COMPONENT_VAULT_BUILD_VERSION", "source")
BUILD_REVISION = os.environ.get("COMPONENT_VAULT_BUILD_REVISION", "unknown")
