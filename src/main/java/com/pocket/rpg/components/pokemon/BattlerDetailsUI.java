package com.pocket.rpg.components.pokemon;

import com.pocket.rpg.components.Component;
import com.pocket.rpg.components.ComponentMeta;
import com.pocket.rpg.components.ComponentReference;
import com.pocket.rpg.components.ComponentReference.Source;
import com.pocket.rpg.components.ui.AlphaGroup;

@ComponentMeta(category = "Pokemon/UI")
public class BattlerDetailsUI extends Component {

    @ComponentReference(source = Source.KEY)
    private AlphaGroup alphaGroup;
}
