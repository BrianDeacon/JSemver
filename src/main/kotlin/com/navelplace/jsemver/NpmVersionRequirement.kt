package com.navelplace.jsemver

import com.navelplace.jsemver.antlr.NPMLexer
import com.navelplace.jsemver.antlr.NPMParser
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer


enum class Operator {
    EQ, GT, LT, GTEQ, LTEQ, TILDE, CARET;

    companion object {
        private val matches = mapOf(
                "=" to "EQ",
                ">" to "GT",
                "<" to "LT",
                "<=" to "LTEQ",
                ">=" to "GTEQ",
                "~" to "TILDE",
                "^" to "CARET")
        fun forString(value: String?): Operator? {
            if (value == null) return null
            return Operator.valueOf(matches.getValue(value.toUpperCase()))
        }
    }




}

interface Claus {
    fun isSatisfiedBy(version: Version): Boolean
}

abstract class Santa : Claus {

    companion object {
        fun clauseFor(context: NPMParser.OperatorClauseContext, rawRequirement: String) : Claus{
            val operator = Operator.forString(context.operator()?.text?.toUpperCase())
            if (DashClaus.isFalseOperator(context, rawRequirement)) {
                return DashClaus(context)
            }
            return when(operator) {
                null, Operator.EQ -> EqualClause(context)
                Operator.TILDE -> TildeClause(context)
                Operator.CARET -> CaretClause(context)
                Operator.LT -> LessThanClause(context)
                Operator.GTEQ -> GreaterThanEqualClause(context)
                Operator.GT -> GreaterThanClause(context)
                Operator.LTEQ -> LessThanEqualClause(context)
            }
        }
    }
    val range: VersionRange

    constructor(context: NPMParser.OperatorClauseContext) {
        this.range = rangeFor(context.version())
    }

    protected abstract fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange

    protected open fun minFor(versionContext: NPMParser.VersionContext): Version {
        val preRelease = versionContext.preRelease()?.dottedLegal()?.legalCharacters()?.map { it.text }?.toTypedArray()
        val build = versionContext.build()?.dottedLegal()?.legalCharacters()?.map { it.text }?.toTypedArray()
        return NpmVersionRequirement.minFor(versionContext.major().text, versionContext.minor()?.text, versionContext.patch()?.text, preRelease, build)
    }

    protected open fun maxFor(versionContext: NPMParser.VersionContext): Version {
        val preRelease = versionContext.preRelease()?.dottedLegal()?.legalCharacters()?.map { it.text }?.toTypedArray()
        val build = versionContext.build()?.dottedLegal()?.legalCharacters()?.map { it.text }?.toTypedArray()
        return NpmVersionRequirement.maxFor(versionContext.major().text, versionContext.minor()?.text, versionContext.patch()?.text, preRelease, build)
    }

    override fun isSatisfiedBy(version: Version): Boolean {
        return range.contains(version)
    }
}

class EqualClause(context: NPMParser.OperatorClauseContext) : Santa(context) {
    override fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange {
        return VersionRange(min = minFor(versionContext), max=maxFor(versionContext), minInclusive = true, maxInclusive = false)
    }
}

class GreaterThanClause(context: NPMParser.OperatorClauseContext) : Santa(context) {
    override fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange {
        return VersionRange(min = minFor(versionContext), max=Version.MAX_VERSION, minInclusive = false, maxInclusive = true)
    }
}

class LessThanClause(context: NPMParser.OperatorClauseContext) : Santa(context) {
    override fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange {
        return VersionRange(min = Version.MIN_VERSION, max=minFor(versionContext), minInclusive = true, maxInclusive = false)
    }
}

class GreaterThanEqualClause(context: NPMParser.OperatorClauseContext) : Santa(context) {
    override fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange {
        return VersionRange(min = minFor(versionContext), max=Version.MAX_VERSION, minInclusive = true, maxInclusive = true)
    }
}

class LessThanEqualClause(context: NPMParser.OperatorClauseContext) : Santa(context) {
    override fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange {
        return VersionRange(min = Version.MIN_VERSION, max=minFor(versionContext), minInclusive = true, maxInclusive = true)
    }
}

class TildeClause(context: NPMParser.OperatorClauseContext) : Santa(context) {
    override fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange {
        return VersionRange(min = minFor(versionContext), max=maxFor(versionContext), minInclusive = true, maxInclusive = false)
    }

    override fun maxFor(versionContext: NPMParser.VersionContext): Version {
        var major = versionContext.major().text.toInt()
        val minorWildcard = NpmVersionRequirement.isWildcard(versionContext.minor()?.text)
        val patchWildcard = NpmVersionRequirement.isWildcard(versionContext.patch()?.text)
        var minor = if (minorWildcard) 0 else versionContext.minor()!!.text.toInt()
        var patch = if (patchWildcard) 0 else versionContext.patch()!!.text.toInt()

        when {
            // ~0 := >=0.0.0 <(0+1).0.0 := >=0.0.0 <1.0.0 (Same as 0.x)
            major == 0 && minorWildcard && patchWildcard -> { major += 1; minor = 0; patch = 0; }
            //~0.2 := >=0.2.0 <0.(2+1).0 := >=0.2.0 <0.3.0 (Same as 0.2.x)
            major == 0 && !minorWildcard && patchWildcard -> { major = major; minor += 1; patch = 0; }
            //~1.2.3 := >=1.2.3 <1.(2+1).0 := >=1.2.3 <1.3.0
            major > 0 && minor > 0 && patch > 0 -> { major = major; minor += 1; patch = 0 }
            //~1.2 := >=1.2.0 <1.(2+1).0 := >=1.2.0 <1.3.0 (Same as 1.2.x)
            major > 0 && minor > 0 && patchWildcard -> { major = major; minor += 1; patch = 0 }
            //~1 := >=1.0.0 <(1+1).0.0 := >=1.0.0 <2.0.0 (Same as 1.x)
            major > 0 && minorWildcard && patchWildcard -> { major += 1; minor = 0; patch = 0 }
            //~0.2.3 := >=0.2.3 <0.(2+1).0 := >=0.2.3 <0.3.0
            major == 0 && !minorWildcard && !patchWildcard -> { major = 0; minor += 1; patch = 0; }
        }
        return Version(major, minor, patch)
    }

    override fun isSatisfiedBy(otherVersion: Version) =
            if (otherVersion.preRelease.isNotBlank()) {
                otherVersion.major == range.min.major &&
                        otherVersion.minor == range.min.minor &&
                        otherVersion.patch == range.min.patch &&
                        range.contains(otherVersion)

            } else {
                range.contains(otherVersion)
            }
}

class CaretClause(context: NPMParser.OperatorClauseContext) : Santa(context) {
    override fun rangeFor(versionContext: NPMParser.VersionContext): VersionRange {
        return VersionRange(min = minFor(versionContext), max=maxFor(versionContext), minInclusive = true, maxInclusive = false)
    }

    override fun isSatisfiedBy(otherVersion: Version) =
        if (otherVersion.preRelease.isNotBlank()) {
            /*
            ^1.2.3-beta.2 := >=1.2.3-beta.2 <2.0.0
            Note that prereleases in the 1.2.3 version will be allowed, if they are greater than or equal to beta.2.
            So, 1.2.3-beta.4 would be allowed, but 1.2.4-beta.2 would not, because it is a prerelease of a different
            [major, minor, patch] tuple.

            ^0.0.3-beta := >=0.0.3-beta <0.0.4
            Note that prereleases in the 0.0.3 version only will be allowed, if they are greater than or equal to beta.
            So, 0.0.3-pr.2 would be allowed.
            */
            otherVersion.major == range.min.major &&
            otherVersion.minor == range.min.minor &&
            otherVersion.patch == range.min.patch &&
            range.contains(otherVersion)

        } else {
            range.contains(otherVersion)
        }

    override fun maxFor(versionContext: NPMParser.VersionContext): Version {
        var major = versionContext.major().text.toInt()
        val minorWildcard = NpmVersionRequirement.isWildcard(versionContext.minor()?.text)
        val patchWildcard = NpmVersionRequirement.isWildcard(versionContext.patch()?.text)
        var minor = if (minorWildcard) 0 else versionContext.minor()!!.text.toInt()
        var patch = if (patchWildcard) 0 else versionContext.patch()!!.text.toInt()
        when {
            // ^0.x := >=0.0.0 <1.0.0
            major + minor + patch == 0 && minorWildcard -> { major = 1; minor = 0; patch = 0; }

            // ^0.0.x := >=0.0.0 <0.1.0
            // ^0.0 := >=0.0.0 <0.1.0
            major + minor + patch == 0 &&  patchWildcard && !minorWildcard -> { major = major; minor += 1; patch = 0 }

            // ^1.2.x := >=1.2.0 <2.0.0
            // ^1.2.3 := >=1.2.3 <2.0.0
            // ^2.3.4 := >=2.3.4 <3.0.0
            // ^1.2.3-beta.2 := >=1.2.3-beta.2 <2.0.0
            // ^1.x := >=1.0.0 <2.0.0
            major > 0 -> { major += 1; minor = 0; patch = 0; }

            // ^0.0.3 := >=0.0.3 <0.0.4
            major == 0 && minor == 0 && !patchWildcard && !minorWildcard -> { major = major; minor = minor; patch += 1; }

            // ^0.2.3 := >=0.2.3 <0.3.0
            major == 0 && !patchWildcard && !minorWildcard -> { major = major; minor += 1; patch = 0; }

            // ^0.0.3 := >=0.0.3 <0.0.4
            major == 0 && minor == 0 && patch > 0 -> { major = major; minor = minor; patch += 1; }
        }

        return Version(major, minor, patch)
    }
}


class DashClaus : Claus {

    companion object {
        private val FALSE_VERSION_MATCHER = """

                """.trim().toRegex()

        private fun hasThisRange(minMajor: String,
                                 minMinor: String,
                                 minPatch: String,
                                 preRelease: String,
                                 rawRequirement: String): Boolean {
            // There was a 1.2.3-4.5.6, which is technically valid if there was no whitespace around the dash.
            // If there is whitespace, we'll treat it as a DashClause

            val escaped = preRelease.replace(".", "\\.")
            val regex = "$minMajor\\.$minMinor\\.$minPatch((\\s+[-]\\s+)|(\\s+[-])|([-]\\s+))$escaped"
            return regex.toRegex().containsMatchIn(rawRequirement)
        }

        fun isFalseOperator(context: NPMParser.OperatorClauseContext, rawRequirement: String): Boolean {
            /*
             Psychotic semver values that are still valid:
             1.1.1-2.2.2 (Prerelease is "2.2.2" which looks like a version)
             1.1.1-2.2.2-SNAPSHOT+build (Prerelease is "2.2.2-SNAPSHOT" because dashes are allowed in the prerelease)

             If the  prerelease looks like a version, AND there is whitespace
             in the raw version that would render the semver invalid, we force it into a DashClaus
             i.e. "1.1.1-2.2.2" is a version with a prerelease. "1.1.1 - 2.2.2" is a DashClause
             Ideally, the ANTLR parser would be able to distinguish "1.1.1 - 2.2.2" from
             "1.1.1-2.2.2"
             This smells like a bug, but it's a compatible bug to npm semver.
            */
            val version = context.version()
            return version.build() == null &&
                    NpmVersionRequirement.isValidPartialVersion(version.preRelease()?.text) &&
                    hasThisRange(version.major().text, version.minor().text, version.patch().text, version.preRelease().text, rawRequirement)


        }
    }
    val versionRange: VersionRange

    constructor(context: NPMParser.DashClauseContext) {
        val min = context.version(0)
        val max = context.version(1)

        var legalMinChars = min.preRelease()?.dottedLegal()?.legalCharacters()
        var legalMaxChars = max.preRelease()?.dottedLegal()?.legalCharacters()
        val preReleaseMin = if (legalMinChars != null) legalMinChars.map { it.text }.toTypedArray() else emptyArray()
        val preReleaseMax = if (legalMaxChars != null) legalMaxChars.map { it.text }.toTypedArray() else emptyArray()

        legalMinChars = min.build()?.dottedLegal()?.legalCharacters()
        legalMaxChars = max.build()?.dottedLegal()?.legalCharacters()
        val buildMin = if (legalMinChars != null) legalMinChars.map { it.text }.toTypedArray() else emptyArray()
        val buildMax = if (legalMaxChars != null) legalMaxChars.map { it.text }.toTypedArray() else emptyArray()

        val minVersion = NpmVersionRequirement.minFor(min.major().text, min.minor()?.text, min.patch()?.text,preReleaseMin, buildMin)
        val maxVersion = NpmVersionRequirement.maxFor(max.major().text, min.minor()?.text, min.patch()?.text, preReleaseMax, buildMax)
        this.versionRange = VersionRange(min = minVersion, max = maxVersion)
    }

    constructor(context: NPMParser.OperatorClauseContext) {
        val minMajor = context.version().major().text.toInt()
        val minMinorString = context.version().minor()?.text
        val minPatchString = context.version().patch()?.text
        val minMinor = if (NpmVersionRequirement.isWildcard(minMinorString)) 0 else minMinorString!!.toInt()
        val minPatch = if (NpmVersionRequirement.isWildcard(minPatchString)) 0 else minPatchString!!.toInt()
        val minVersion = Version(minMajor, minMinor, minPatch)

        val parser = NpmVersionRequirement.parserFor(context.version().preRelease()!!.text)
        val version = parser.version()
        val maxMajorString = version.major().text
        val maxMinorString = version.minor()?.text
        val maxPatchString = version.patch()?.text

        val maxMajor = maxMajorString.toInt()
        val maxMinor = if (NpmVersionRequirement.isWildcard(maxMinorString)) 0 else maxMinorString!!.toInt()
        val maxPatch = if (NpmVersionRequirement.isWildcard(maxPatchString)) 0 else maxPatchString!!.toInt()


        val maxVersion = Version(maxMajor, maxMinor, maxPatch)

        this.versionRange = VersionRange(min = minVersion, max = maxVersion)
    }

    override fun isSatisfiedBy(version: Version): Boolean {
        return version.preReleaseElements.isEmpty() &&
                version.metadataElements.isEmpty() &&
            versionRange.contains(version)
    }
}

class AndClaus(val predicates: Collection<Claus>): Claus {
    override fun isSatisfiedBy(version: Version) = predicates.all { it.isSatisfiedBy(version) }
}

class OrClaus(val predicates: Collection<Claus>): Claus {
    override fun isSatisfiedBy(version: Version) = predicates.any { it.isSatisfiedBy(version) }
}

class NpmVersionRequirement : VersionRequirement {

    companion object {
        fun isWildcard(value: String?): Boolean {
            return value == null ||
                    value.isNullOrBlank() ||
                    value.toUpperCase() == "X"
        }

        fun isValidPartialVersion(version: String?) =
            try {
                version != null &&
                NpmVersionRequirement.parserFor(version).version()?.major()?.text != null
            } catch (e: InvalidRequirementFormatException) {
                false
            }


        internal fun parserFor(value: String): NPMParser {
            val lexer = NPMLexer(CharStreams.fromString(value))
            val parser = NPMParser(CommonTokenStream(lexer))
            val listener = ThrowingRequirementErrorListener(value)
            lexer.addErrorListener(listener)
            parser.addErrorListener(listener)
            return parser
        }

        private fun validate(major: String, minor: String?, patch: String?, preRelease: Array<String>? = null, build: Array<String>? = null) {
            val preReleaseArray = preRelease ?: emptyArray()
            val buildArray = build ?: emptyArray()
            if (isWildcard(minor) && !isWildcard(patch)) {
                throw InvalidRequirementFormatException("$major.$minor.$patch")
            }
            if (
                    (isWildcard(minor) || isWildcard(patch)) &&
                    (preReleaseArray.isNotEmpty() || buildArray.isNotEmpty())
            ) {
                val pre = preReleaseArray.joinToString(".")
                val b = buildArray.joinToString(".")
                //TODO: This might not format the error correctly if pre or b are empty
                throw InvalidRequirementFormatException("$major.$minor.$patch-$pre+$b")
            }
        }
        internal fun maxFor(major: String, minor: String?, patch: String?, preRelease: Array<String>? = null, build: Array<String>? = null): Version {
            var majorInt = major.toInt()
            var minorInt = if (minor?.toUpperCase() == "X" || minor.isNullOrBlank()) 0 else minor!!.toInt()
            var patchInt = if (patch?.toUpperCase() == "X" || patch.isNullOrBlank()) 0 else patch!!.toInt()

            var preReleaseArray = preRelease ?: emptyArray()
            var buildArray = build ?: emptyArray()
            if (isWildcard(minor) || isWildcard(patch)) {
                preReleaseArray = emptyArray()
                buildArray = emptyArray()
            }




            if (!isWildcard(patch) && isWildcard(minor)) {
                throw InvalidRequirementFormatException("$major.$minor.$patch")
            }
            if (isWildcard(patch) && isWildcard(minor)) {
                majorInt++
            }
            if (isWildcard(patch) && !isWildcard(minor)) {
                minorInt++
            }

            validate(major, minor, patch, preRelease, build)
            return Version(majorInt, minorInt, patchInt, preReleaseArray, buildArray)
        }

        internal fun minFor(major: String, minor: String?, patch: String?, preRelease: Array<String>? = null, build: Array<String>? = null): Version {
            val majorInt = major.toInt()
            val minorInt = if (minor?.toUpperCase() == "X" || minor.isNullOrBlank()) 0 else minor!!.toInt()
            val patchInt = if (patch?.toUpperCase() == "X" || patch.isNullOrBlank()) 0 else patch!!.toInt()
            var preReleaseArray = preRelease ?: emptyArray()
            var buildArray = build ?: emptyArray()
            if (isWildcard(minor) || isWildcard(patch)) {
                preReleaseArray = emptyArray()
                buildArray = emptyArray()
            }


            if (patch != "X" && minor == "X") {
                throw InvalidRequirementFormatException("$major.$minor.$patch")
            }
            validate(major, minor, patch, preRelease, build)
            return Version(majorInt, minorInt, patchInt, preReleaseArray, buildArray)
        }
    }

    val orClaus: Claus

    constructor(rawRequirement: String): super(rawRequirement.trim(), RequirementType.NPM) {
        val parser = parserFor(this.rawRequirement)
        val intersections = parser.union().intersection()
        this.orClaus = OrClaus(intersections.map { intersection ->
            val operatorClauses: Collection<Claus> = intersection.operatorClause().map { Santa.clauseFor(it, this.rawRequirement) }
            val dashClauses = intersection.dashClause().map { DashClaus(it) }
            val clauses = ArrayList<Claus>()
            clauses.addAll(operatorClauses)
            clauses.addAll(dashClauses)
            AndClaus(clauses)
        })
    }

    override fun isSatisfiedBy(version: Version) = orClaus.isSatisfiedBy(version)
    override fun calculate(rawRequirement: String) = emptyArray<VersionRange>()



}

/**
 * @suppress
 */
class ThrowingRequirementErrorListener(val input: String) : BaseErrorListener() {
    override fun syntaxError(recognizer: Recognizer<*, *>, offendingSymbol: Any?, line: Int, charPositionInLine: Int, msg: String, e: RecognitionException?) {
        throw InvalidRequirementFormatException(input)
    }
}