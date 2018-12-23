package github.vrih.xsub.domain

/**
 * @author Sindre Mehus
 * @version $Id$
 */
enum class RepeatMode {
    OFF {
        override operator fun next(): RepeatMode {
            return ALL
        }
    },
    ALL {
        override operator fun next(): RepeatMode {
            return SINGLE
        }
    },
    SINGLE {
        override operator fun next(): RepeatMode {
            return OFF
        }
    };

    abstract operator fun next(): RepeatMode
}
