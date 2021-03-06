package com.navelplace.jsemver.npm

import com.navelplace.jsemver.Version
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NpmVersionRequirementTest {

    @Test
    fun `can parse a raw version`() {
        val vString = "99.88.77"
        val requirement = NpmVersionRequirement("v$vString")
        assertTrue { requirement.isSatisfiedBy(vString) }
        assertFalse { requirement.isSatisfiedBy("1.2.3") }
    }

    @Test
    fun `can parse a raw version with two Xs`() {
        val vString = "99.x.x"
        val requirement = NpmVersionRequirement("v$vString")
        assertTrue { requirement.isSatisfiedBy("99.0.0") }
        assertTrue { requirement.isSatisfiedBy("99.1.1") }
    }

    @Test
    fun `can parse a raw version with one X`() {
        val vString = "99.99.x"
        val requirement = NpmVersionRequirement("v$vString")
        assertTrue { requirement.isSatisfiedBy("99.99.0") }
        assertTrue { requirement.isSatisfiedBy("99.99.1") }
    }

    @Test
    fun `various ranges`() {
        val map = mapOf(
                "1.2.X" to arrayOf("1.2.0", "1.2.1","1.2.10"),
                "1.x.x" to arrayOf("1.0.0", "1.9.1","1.0.1", "1.2.3"),
                "1.2.3" to arrayOf("1.2.3"),
                "v1.2.3" to arrayOf("1.2.3"),
                "V1.x.x" to arrayOf("1.0.0", "1.9.1","1.0.1", "1.2.3"),
                "v1.2.X" to arrayOf("1.2.0", "1.2.1")
        )
        map.forEach {key, versions ->
            val requirement = NpmVersionRequirement(key)
            assertTrue(versions.all { requirement.isSatisfiedBy(it) })
        }
    }

    @Test
    fun `Simple AND with operators`() {
        val req = NpmVersionRequirement(">1.2.3 <4.5.6")
        assertTrue { req.isSatisfiedBy("2.0.0") }
    }

    @Test
    fun `Can parse a simple AND`() {
        val requirement = NpmVersionRequirement("1.2.3 4.5.6 || 3.3.3-SNAPSHOT 2.2.2-foo.bar+baz.blif")
        //It's an impossible requirement
        assertFalse { requirement.isSatisfiedBy("1.2.3") }

    }



    @Test
    fun `caret ranges`() {
        val satisfies = mapOf(
                "^1.2.3" to arrayOf("1.2.3", "1.2.4", "1.9.9"),
                "^1.2.x" to arrayOf("1.2.0", "1.9.9", "1.2.1"),
                "^2.3.4" to arrayOf("2.3.4", "2.9.9", "2.4.4", "2.3.5"),
                "^1.2.3-beta.2" to arrayOf("1.2.3-beta.2", "1.2.3-beta.3", "1.9.9", "1.2.3", "1.2.4"),
                "^1.x" to arrayOf("1.0.0", "1.9.9", "1.1.0", "1.0.1", "1.1.1"),
                "^0.2.3" to arrayOf("0.2.3"),
                "^0.0.3" to arrayOf("0.0.3"),
                "^0.0.x" to arrayOf("0.0.0", "0.0.9"),
                "^0.0" to arrayOf("0.0.0", "0.0.1"),
                "^0.x" to arrayOf("0.0.0", "0.9.9", "0.1.0" )
        )

        val doesNotSatisfy = mapOf(
                "^1.2.3" to arrayOf("1.2.2", "2.0.0", "0.9.9", "2.0.1", "1.2.3-alpha", "1.2.3-alpha+build", "1.2.4-alpha"),
                "^1.2.x" to arrayOf("1.1.9", "2.0.0", "1.2.0-alpha", "1.2.0-alpha+build", "1.2.1-alpha"),
                "^2.3.4" to arrayOf("2.3.3", "3.0.0", "3.0.1", "3.0.0-alpha", "3.0.0-alpha+build", "2.3.5-alpha"),
                "^1.2.3-beta.2" to arrayOf("1.2.2", "1.2.4-beta.5", "1.2.3-alpha", "1.2.3-alpha.5", "1.2.5-x", "2.0.0-alpha"),
                "^1.x" to arrayOf("0.9.9", "2.0.0", "2.0.0-alpha", "2.0.0-alpha+build", "1.0.0-alpha", "1.0.0-alpha+build", "1.0.1-alpha"),
                "^0.2.3" to arrayOf("0.3.0", "1.0.0", "0.2.2", "0.2.3-alpha", "0.2.3-alpha+build", "0.2.4-alpha"),
                "^0.0.3" to arrayOf("0.0.2", "0.0.3-alpha", "1.0.0", "0.1.0", "0.0.3-alpha", "0.0.3-alpha+build", "0.0.4-alpha", "0.0.4"),
                "^0.0.x" to arrayOf("0.1.0", "0.0.1-alpha", "0.0.0-alpha+build"),
                "^0.0" to arrayOf("0.0.0-alpha", "0.0.0-alpha+build", "0.1.0", "0.1.0-alpha", "0.1.0-alpha.build"),
                "^0.x" to arrayOf("1.0.0", "1.0.0-alpha", "0.0.0-alpha", "0.0.0-alpha+build")
        )

        Version("1.2.3").satisfies(NpmVersionRequirement("^1.2.3"))
        satisfies.forEach { requirement, versions ->
            versions.forEach {version ->
                assertTrue ("$version should satisfy $requirement",
                        { Version(version).satisfies(NpmVersionRequirement(requirement)) })

            }
        }

        doesNotSatisfy.forEach { requirement, versions ->
            versions.forEach {version ->
                assertFalse ("$version should not satisfy $requirement",
                        { Version(version).satisfies(NpmVersionRequirement(requirement)) })

            }
        }
    }

    @Test
    fun `preRelease still disqualifies a caret upper bound`() {
        val version3 = "3.0.0"
        val version2 = "2.9.9"
        val alpha = "3.0.0-alpha"
        val req = NpmVersionRequirement("^2.3.4")
        assertTrue { Version(version2).satisfies(req) }
        assertFalse { Version(version3).satisfies(req) }
        assertFalse { Version(alpha).satisfies(req) }
    }

    @Test
    fun `Caret rules should handle prerelease requirements`() {
        val version = Version("1.2.3-beta.2")
        val requirement = NpmVersionRequirement("^1.2.3-beta.2")
        assertTrue(version.satisfies(requirement))
    }

    @Test
    fun `v0_0_4 should not satisfy caret0_0_3`() {
        assertFalse(Version("0.0.4").satisfies(NpmVersionRequirement("^0.0.3")))
    }

    @Test
    fun `v0_1_0 should not satisfy caret0_0_x`() {
        assertFalse(Version("0.1.0").satisfies(NpmVersionRequirement("^0.0.x")))
    }

    @Test
    fun `version 1_0_0 and requirement 1_0_0 - 2_0_0`() {
        assertTrue(Version("1.0.0").satisfies(NpmVersionRequirement("1.0.0 - 2.0.0")))
    }

    @Test
    fun `version 1_0_0 and requirement 1_0_0 -2_0_0`() {
        val version = "1.0.0"
        val requirement = " 1.0.0 - 2.0.0"
        assertTrue ("$version should satisfy $requirement",
                { Version(version).satisfies(NpmVersionRequirement(requirement)) })

    }


    @Test
    fun `can parse a lone version`() {
        val parser = NpmVersionRequirement.parserFor("2.0.0")
        val intersections = parser.union().intersection()
        assertEquals(1, intersections.size)
        val intersection = intersections[0]
        assertNotNull(intersection)
        val clauses = intersection.operatorClause()
        assertNotNull(clauses)
        assertEquals(1, clauses.size)
        val clause = clauses[0]
        val version = clause.version()
        assertNotNull(version)
        assertNotNull(version.major())
        assertEquals(version.major().text, "2")
        assertEquals(version.minor().text, "0")
        assertEquals(version.patch().text, "0")
    }

    @Test
    fun `dash ranges` () {
        val satisfies = mapOf(
                "1.0.0 - 2.0.0" to arrayOf("1.0.0", "1.1.0", "2.0.0"),
                "1.0 - 2.0.0" to arrayOf("1.0.0", "1.1.0", "2.0.0"),
                "1.0.0 - 2.0" to arrayOf("1.0.0", "1.1.0", "2.0.0"),
                "1.0.0 -2.0.0" to arrayOf("1.0.0", "1.1.0", "2.0.0"),
                " 1.0.0 -2.0.0 " to arrayOf("1.0.0", "1.1.0", "2.0.0"),
                " 1.0.0- 2.0.0 " to arrayOf("1.0.0", "1.1.0", "2.0.0"),
                "0.0.0 - 1.0.0 " to arrayOf("0.0.0", "1.0.0", "0.0.1", "0.1.0")
        )

        val doesNotSatisfy = mapOf(
                "1.0.0 - 2.0.0" to arrayOf("0.0.1", "0.1.0", "2.0.1", "2.1.0", "1.0.0-alpha", "1.0.0-alpha+build", "1.1.1-alpha"),
                "1.0.0 -2.0.0" to arrayOf("0.0.1", "0.1.0", "2.0.1", "2.1.0", "1.0.0-alpha", "1.0.0-alpha+build", "1.1.1-alpha"),
                " 1.0.0 -2.0.0 " to arrayOf("0.0.1", "0.1.0", "2.0.1", "2.1.0", "1.0.0-alpha", "1.0.0-alpha+build", "1.1.1-alpha"),
                "1.0.0- 2.0.0" to arrayOf("0.0.1", "0.1.0", "2.0.1", "2.1.0", "1.0.0-alpha", "1.0.0-alpha+build", "1.1.1-alpha"),
                "0.0.0 - 1.0.0" to arrayOf("1.0.1", "1.1.0", "2.0.1", "2.1.0", "0.0.0-alpha", "0.0.0-alpha+build", "0.1.1-alpha")
        )
        assertTrue { Version("1.1.0").satisfies(NpmVersionRequirement("1.0.0 -2.0.0"))}

        satisfies.forEach { requirement, versions ->
            versions.forEach {version ->
                try {
                    assertTrue ("$version should satisfy $requirement",
                            { Version(version).satisfies(NpmVersionRequirement(requirement)) })
                } catch(e: AssertionError) {
                    throw e
                } catch (e: Exception) {
                    throw RuntimeException("Problem with version $version and requirement $requirement", e)
                }


            }
        }

        doesNotSatisfy.forEach { requirement, versions ->
            versions.forEach {version ->
                try {
                    assertFalse ("$version should not satisfy $requirement",
                            { Version(version).satisfies(NpmVersionRequirement(requirement)) })
                } catch(e: AssertionError) {
                    throw e
                } catch (e: Exception) {
                    throw RuntimeException("Problem with version $version and requirement $requirement", e)
                }


            }
        }
    }

    @Test
    fun `from the npm docs` () {
        val versions = arrayOf("1.2.3", "8.5.0", "1.0.0", "5.1.0")
        val requirement = "1.x || >=8.5.0 || 5.0.0 - 7.2.3"
        versions.forEach {version ->
            try {
                assertTrue ("$version should satisfy $requirement",
                        { Version(version).satisfies(NpmVersionRequirement(requirement)) })
            } catch(e: AssertionError) {
                throw e
            } catch (e: Exception) {
                throw RuntimeException("Problem with version $version and requirement $requirement", e)
            }


        }
    }

    @Test
    fun `Correctly escapes the letter v`() {
        val v1Clean = "1.1.1-vvv.2"
        val v1 = "v$v1Clean"
        val v2Clean = "1.1.2-vvv.2"
        val v2 = "v$v2Clean"
        val v1Plus = "1.1.1-vvv.3"
        val v1Minus = "1.1.1-avvv.3"
        val requirement = NpmVersionRequirement("$v1 || $v2")
        assertTrue { requirement.isSatisfiedBy(v1Clean) }
        assertTrue { requirement.isSatisfiedBy(v2Clean) }
        assertFalse {requirement.isSatisfiedBy(v1Minus)}
        assertFalse {requirement.isSatisfiedBy(v1Plus)}
    }

    @Test
    fun `Correctly wildcards X`() {
        val requirement = NpmVersionRequirement("1.x")
        val requirement2 = NpmVersionRequirement("1.X")
        val requirement3 = NpmVersionRequirement("1.*")
        arrayOf("1.1.1", "1.0.0", "1.0.1").forEach {version ->
            arrayOf(requirement, requirement2, requirement3).forEach { req ->
                assertTrue {  req.isSatisfiedBy(version) }
            }
        }
    }

    @Test
    fun `escaping dashes`() {
        val requirement = NpmVersionRequirement("123.123.123-asdf || 4.4.4-wer")
        arrayOf("123.123.123-asdf", "4.4.4-wer").forEach {
            assertTrue { requirement.isSatisfiedBy(it) }
        }
    }

    @Test
    fun `version numbers hidden in the prerelease string`() {
        val version = "1.2.3-SNASHOT1.2.3"
        val version2 = "1.2.3-X1.2.3"
        assertTrue(Version(version2).newerThan(Version(version)))
        val requirement = NpmVersionRequirement(version)
        assertTrue(requirement.isSatisfiedBy(version))
        assertTrue(NpmVersionRequirement(">=1.2.3-SNASHOT1.2.3").isSatisfiedBy("1.2.3-X1.2.3"))
        assertFalse(NpmVersionRequirement("<=1.2.3-SNASHOT1.2.3").isSatisfiedBy("1.2.3-X1.2.3"))
        assertFalse(requirement.isSatisfiedBy("1.2.3-SNASHOT1.0.0"))
    }

    @Test
    fun `tilde ranges` () {
        /*
        ~1.2.3 := >=1.2.3 <1.(2+1).0 := >=1.2.3 <1.3.0
~1.2 := >=1.2.0 <1.(2+1).0 := >=1.2.0 <1.3.0 (Same as 1.2.x)
~1 := >=1.0.0 <(1+1).0.0 := >=1.0.0 <2.0.0 (Same as 1.x)
~0.2.3 := >=0.2.3 <0.(2+1).0 := >=0.2.3 <0.3.0
~0.2 := >=0.2.0 <0.(2+1).0 := >=0.2.0 <0.3.0 (Same as 0.2.x)
~0 := >=0.0.0 <(0+1).0.0 := >=0.0.0 <1.0.0 (Same as 0.x)
~1.2.3-beta.2 := >=1.2.3-beta.2 <1.3.0
         */
        val satisfies = mapOf(
                "~1.2.3" to arrayOf("1.2.3", "1.2.5"),
                "~1.2" to arrayOf("1.2.0", "1.2.1", "1.2.99"),
                "~1" to arrayOf("1.0.0", "1.1.0", "1.0.1", "1.9.9"),
                "~0.2.3" to arrayOf("0.2.3", "0.2.9"),
                "~0.2" to arrayOf("0.2.0", "0.2.9"),
                "~0" to arrayOf("0.0.0", "0.9.0", "0.0.9", "0.9.9"),
                "~1.2.3-beta.2" to arrayOf("1.2.3-beta.2", "1.2.3", "1.2.9", "1.2.3-beta.3", "1.2.3-gamma", "1.2.3-beta.2+build", "1.2.3-beta.3+build")
        )

        val doesNotSatisfy = mapOf(
                "~1.2.3" to arrayOf("1.2.2", "1.3.0", "1.2.2-beta", "1.2.2-beta+build", "0.0.0", "1.3.0-beta"),
                "~1.2" to arrayOf("1.1.9", "1.3.0", "1.2.0-beta", "1.2.0-beta+build", "1.3.0-beta"),
                "~1" to arrayOf("0.9.9", "1.0.0-beta", "2.0.0", "2.0.0-alpha"),
                "~0.2.3" to arrayOf("0.3.0", "0.2.2", "0.2.3-alpha", "0.2.3-alpha+build", "0.3.0-alpha"),
                "~0.2" to arrayOf("0.1.9", "0.2.0-alpha", "0.2.0-alpha+build", "0.3.0", "0.3.0-alpha", "0.3.0-alpha+build"),
                "~0" to arrayOf("0.0.0-alpha", "0.0.0-alpha+build", "1.0.0", "1.0.0-alpha", "1.0.0-alpha+build"),
                "~1.2.3-beta.2" to arrayOf("1.2.3-beta.1", "1.2.2", "1.2.4-gamma", "1.3.0", "1.3.0-beta.6", "1.3.1")
        )

        satisfies.forEach { requirement, versions ->
            versions.forEach {version ->
                try {
                    assertTrue ("$version should satisfy $requirement",
                            { Version(version).satisfies(NpmVersionRequirement(requirement)) })
                } catch(e: AssertionError) {
                    throw e
                } catch (e: Exception) {
                    throw RuntimeException("Problem with version $version and requirement $requirement", e)
                }


            }
        }

        doesNotSatisfy.forEach { requirement, versions ->
            versions.forEach {version ->
                try {
                    assertFalse ("$version should not satisfy $requirement",
                            { Version(version).satisfies(NpmVersionRequirement(requirement)) })
                } catch(e: AssertionError) {
                    throw e
                } catch (e: Exception) {
                    throw RuntimeException("Problem with version $version and requirement $requirement", e)
                }


            }
        }
    }

}
