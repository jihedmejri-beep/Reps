"""Provider registry package.

Importing this package registers every built-in provider (see
`providers/base.py` for the plug-in contract) as a side effect. `run.py`
and the pipeline never import a concrete provider module directly -- they
only ask the registry for a provider by name, which is what keeps adding
a new data source a matter of dropping in a new module here.
"""

from providers import custom, exercisedb, gymvisual, wger  # noqa: F401
from providers.base import ExerciseProvider, available_providers, get_provider_class, register_provider

__all__ = [
    "ExerciseProvider",
    "available_providers",
    "get_provider_class",
    "register_provider",
]
