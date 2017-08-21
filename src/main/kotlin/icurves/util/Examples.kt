package icurves.util

import icurves.description.Description
import java.util.*

/**
 *
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
object Examples {

    val list = ArrayList<Pair<String, Description> >()

    init {
        add("Venn-3", "a b c abc ab ac bc")
        add("Venn-4", "a b c d ab ac ad bc bd cd abc abd acd bcd abcd")
        add("Venn-5", "a b c d e ab ac ad ae bc bd be cd ce de abc abd abe acd ace ade bcd bce bde cde abcd abce abde acde bcde abcde")

        add("Nested Piercing 1", "ab ac ad ae abc ade")

        add("Single Piercing 1", "a b c ab ac af ag bc be cd abc abe abf abg acd acf afg bcd bce abcd abce abcf abfg ah abh")
        add("Single Piercing 2", "a b c ab ac af bc be cg ch abc abe abf abj bcd bch cgh abcd abcj")
        add("Single Piercing 3", "a b c ab ac bc bd be bf bx cg ch ci abc bcd bce bcf bcg bch bci bfx")

        add("Double Piercing", "a b c ab ac af ag bc be cd abc abe abf abg acd acf afg bcd bce abcd abce abcf abfg")
        add("Double Piercing 1", "a b c d ab ac ad ae bc bd cd abc abd acd ace bcd abcd acde")
        add("Double Piercing 2", "p q r pq pr qr qs rs pqs prs qrs qrt pqrs")
        add("Double Piercing 3", "a b c d ac ad bc bd cd ce df abd acd ace bcd bce bdf cdf abcd abce bcdf")
        add("Double Piercing 4", "a b d e ac ad ae bc bd cd de")

        add("Combined Piercing 1", "a b c ab ac af bc be cg ch co abc abe abf abj aco bcd bch bco cgh abcd abcj abco")
        add("Combined Piercing 2", "a b c d e f k ab ac ak bc bd bu ce ef abc abu bcu abcg abcl abcu abcgl")
        add("Combined Piercing 3", "a b c d e f g h j k ab ac ai aj bc bd bk ce ck df fg gh kl km kn abc abi aci bck ckl kmn abci")

        add("Combined All 1", "a b c ab ac af bc be cg ch ck co abc abe abf abj ack aco bcd bch bck bco cgh cko abcd abcj abco acko bcko")
        add("Combined All 2", "a b c ab ac ad af bc be cd cg ch abc abe abf abj acd bcd bch cgh abcd abci abcj")
        add("Combined All 3", "a b c j ab ac aj ao bc bd be bf cg ch ci abc abj aco ajo bcd bce bcf bcg bch bci")
        add("Combined All 4", "b c d f h j ab ac bc bd bf bj cf de dj fh hi abc acf bfg abcf")

        add("Edge Route", "a b c ab ac bc bd bf abc abd abf bcd bcf bdf abcd abdf bcdf")
        add("Edge Route 1", "a b c d ab ac ad bc bd be cd abc abd abe acd bcd bce bde abcd abce abde")
        add("Edge Route 2", "a b c d ab ac ad ae af bc bd cd abc abd acd ace acf adf bcd abcd acde adef")

        add("Multidiagram 1", "a b c abc ab ac bc d e de df")
    }

    private fun add(name: String, description: String) {
        list.add(name.to(Description.from(description)))
    }
}