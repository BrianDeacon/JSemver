package com.navelplace.jsemver

/**
 * An inclusively or exclusively bounded range of Versions
 *
 * @property min The mimimum Version
 * @property minInclusive Whether the range includes the minimum bound
 * @property max The maximum Version
 * @property maxInclusive Whether the range includes the maximum bound
 *
 * @author Brian Deacon (bdeacon@navelplace.com)
 */
class VersionRange(val min: Version, val minInclusive: Boolean = true, val max: Version, val maxInclusive: Boolean = true) {

    /**
     * @suppress
     */
    companion object {
        /**
         * All [Version] instances match this [VersionRange]
         */
        val MATCH_ALL = VersionRange(min= Version.MIN_VERSION, max = Version.MAX_VERSION, minInclusive = true, maxInclusive = true)

        /**
         * No [Version] instances ever match this [VersionRange]
         */
        val MATCH_NONE = VersionRange(min= Version.MAX_VERSION, max = Version.MIN_VERSION, minInclusive = false, maxInclusive = false)
    }
    /**
     * Tests a [Version] for inclusion in this range
     *
     * @param version The version to test for inclusion
     * @return True of the [version] is part of this range
     */
    fun contains(version: Version): Boolean {
        if (minInclusive && version.equivalentTo(min)) return true
        if (maxInclusive && version.equivalentTo(max)) return true
        return version.newerThan(min) && version.olderThan(max)
    }

    /**
     * @suppress
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VersionRange

        if (min != other.min) return false
        if (minInclusive != other.minInclusive) return false
        if (max != other.max) return false
        if (maxInclusive != other.maxInclusive) return false

        return true
    }

    /**
     * @suppress
     */
    override fun hashCode(): Int {
        var result = min.hashCode()
        result = 31 * result + minInclusive.hashCode()
        result = 31 * result + max.hashCode()
        result = 31 * result + maxInclusive.hashCode()
        return result
    }

    /**
     * @suppress
     */
    override fun toString(): String {
        return "VersionRange(min=$min, minInclusive=$minInclusive, max=$max, maxInclusive=$maxInclusive)"
    }


}
