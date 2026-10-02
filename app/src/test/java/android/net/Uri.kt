package android.net

/**
 * Minimal test double for [android.net.Uri]. The android.jar on the unit-test classpath
 * only contains stubs that throw; this shadow class lets pure-JVM tests build models
 * that reference content URIs. Resolution order puts test classes first, so this wins.
 */
class Uri private constructor(private val value: String) {

    override fun toString(): String = value

    override fun equals(other: Any?): Boolean = other is Uri && other.value == value

    override fun hashCode(): Int = value.hashCode()

    companion object {
        @JvmStatic
        fun parse(uriString: String?): Uri = Uri(uriString.orEmpty())

        @JvmStatic
        fun encode(s: String?): String = s.orEmpty()

        @JvmStatic
        fun decode(s: String?): String = s.orEmpty()
    }
}
