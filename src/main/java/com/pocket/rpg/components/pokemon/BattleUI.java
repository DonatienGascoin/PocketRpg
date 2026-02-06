package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.UiKeyReference;
import com.pocket.rpg.components.ui.UIImage;

@ComponentMeta(category = "Pokemon/UI")
public class BattleUI extends Component {

    @UiKeyReference
    private UIImage dialogueBg;

    @UiKeyReference
    private UIImage opponentDetailsBg;

    @UiKeyReference
    private UIImage playerDetailsBg;
}
