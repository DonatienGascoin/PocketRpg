package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.ui.UIImage;

@ComponentMeta(category = "Pokemon/UI")
public class BattleUI extends Component {

    @ComponentReference(source = Source.KEY)
    private UIImage dialogueBg;

    @ComponentReference(source = Source.KEY)
    private UIImage opponentDetailsBg;

    @ComponentReference(source = Source.KEY)
    private UIImage playerDetailsBg;
}
