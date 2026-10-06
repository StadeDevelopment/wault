package net.wault.generator

object Wordlist {

    val DEFAULT: List<String> = (
        "abbey acorn actor adapt admit adobe adopt agent agile alarm album alert alien alley alloy alpha " +
            "amber amble amend ample amuse angel anger angle ankle apple apron arbor arena argue arise armor " +
            "arrow asset atlas attic audio audit avoid award aware bacon badge bagel baker balmy banjo barge " +
            "basil basin batch beach beard beast bench berry birch bison blade blaze bliss block bloom blush " +
            "board bonus boost booth brace braid brain brass brave bread brick bride brief brisk broad brook " +
            "brush bugle bunch burst cabin cable cadet camel canal candy canoe cargo carol carry carve cedar " +
            "chain chair chalk charm chase cheek cheer chess chest chief chime chirp chord chose cider cigar " +
            "cinch civic civil claim clamp clash clasp clean clear clerk cliff climb cloak clock cloud clove " +
            "clown coach coast cobra cocoa colon color comet comic coral cover crane crate crawl cream creek " +
            "crest crisp crown crumb crust cubic curve cycle daisy dance dandy dealt debut decal decay decoy " +
            "delta dense depot depth derby detox diary digit dimly diner dingo ditch diver dodge dolly donor " +
            "donut doubt dough dozen draft drain drake drama dream dress drift drill drink drive drone dusty " +
            "eagle early earth easel eaten ebony edict eight elbow elder elite ember emoji empty enact ended " +
            "enemy enjoy enter entry equal equip erase essay ether evade event every exact exile exist extra " +
            "fable facet faint fairy faith false fancy fatal fauna favor feast fence ferry fetch fever fiber " +
            "field fiery fifth fifty final finch first flair flame flank flash fleet flesh flick flint float"
        ).split(" ")

    val BITS_PER_WORD: Double = log2(DEFAULT.size.toDouble())
}
