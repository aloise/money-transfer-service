package name.aloise.utils.generic

inline fun <T : Any, R> T?.fold(map: (T) -> R, orElse: () -> R): R =
    if (this != null) map(this) else orElse()