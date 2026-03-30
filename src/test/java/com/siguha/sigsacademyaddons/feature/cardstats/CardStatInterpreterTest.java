package com.siguha.sigsacademyaddons.feature.cardstats;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CardStatInterpreterTest {

    @Test
    void loadsScalarValuesFromConfigJson() {
        String json = """
                {
                  "values": {
                    "base": [0.4, 0.7, 1.0, 1.1, 1.2, 1.3, 1.4, 2.0, 2.5, 3.0]
                  }
                }
                """;

        CardStatInterpreter.CardScalarTables scalars =
                CardStatInterpreter.CardScalarTables.load(new StringReader(json));

        assertEquals(2.0d, scalars.getScalar("base", 8), 0.000001d);
        assertEquals(1.3d, scalars.getScalar("base", 6), 0.000001d);
    }

    @Test
    void evaluatesGradeScaledModifierValues() {
        CardStatInterpreter.CardScalarTables scalars = CardStatInterpreter.CardScalarTables.of(Map.of(
                "base", List.of(0.4d, 0.7d, 1.0d, 1.1d, 1.2d, 1.3d, 1.4d, 2.0d, 2.5d, 3.0d)
        ));

        CompoundTag modifier = modifier("shiny_chance", 0.024d);

        assertEquals(0.048d, CardStatInterpreter.evaluateModifierValue(modifier, 8, scalars), 0.000001d);
        assertEquals(0.024d, CardStatInterpreter.evaluateModifierValue(modifier, 0, scalars), 0.000001d);
    }

    @Test
    void collectsAlbumStatsUsingNormalizedKeys() {
        CardStatInterpreter.CardScalarTables scalars = CardStatInterpreter.CardScalarTables.of(Map.of(
                "base", List.of(0.4d, 0.7d, 1.0d, 1.1d, 1.2d, 1.3d, 1.4d, 2.0d, 2.5d, 3.0d)
        ));

        CardStatInterpreter interpreter = new CardStatInterpreter();

        CompoundTag root = album(
                card(8, modifier("shiny_chance", 0.024d), modifier("capture_experience_t1", 0.049d)),
                card(8, modifier("shiny_chance", 0.022d)),
                card(8, modifier("ev_yield/cobblemon:defence_t1", 0.25d))
        );

        Map<String, Double> stats = interpreter.collectCardStats(root, scalars);

        assertEquals(0.092d, stats.get("shiny_chance"), 0.000001d);
        assertEquals(0.098d, stats.get("capture_experience"), 0.000001d);
        assertEquals(0.5d, stats.get("ev_yield/cobblemon:defence"), 0.000001d);
    }

    private static CompoundTag album(CompoundTag... cards) {
        ListTag items = new ListTag();
        for (CompoundTag card : cards) {
            items.add(card);
        }

        CompoundTag container = new CompoundTag();
        container.put("items", items);

        CompoundTag components = new CompoundTag();
        components.put("academy:card_album_container", container);

        CompoundTag root = new CompoundTag();
        root.put("components", components);
        return root;
    }

    private static CompoundTag card(int grade, CompoundTag... modifiers) {
        ListTag modifierList = new ListTag();
        for (CompoundTag modifier : modifiers) {
            modifierList.add(modifier);
        }

        CompoundTag cardData = new CompoundTag();
        cardData.putInt("grade", grade);
        cardData.put("modifiers", modifierList);

        CompoundTag components = new CompoundTag();
        components.put("academy:card", cardData);

        CompoundTag entry = new CompoundTag();
        entry.put("components", components);
        return entry;
    }

    private static CompoundTag modifier(String source, double value) {
        CompoundTag assignValue = new CompoundTag();
        assignValue.putDouble("value", value);

        CompoundTag assign = new CompoundTag();
        assign.putString("type", "assign");
        assign.put("value", assignValue);

        CompoundTag scalar = new CompoundTag();
        scalar.putString("type", "card_scalar");
        scalar.putString("id", "base");

        ListTag valueModifiers = new ListTag();
        valueModifiers.add(assign);
        valueModifiers.add(scalar);

        CompoundTag valueNode = new CompoundTag();
        valueNode.put("modifiers", valueModifiers);

        CompoundTag attribute = new CompoundTag();
        attribute.putString("type", "add");
        attribute.put("value", valueNode);

        CompoundTag modifier = new CompoundTag();
        modifier.putString("source", source);
        modifier.put("attribute", attribute);
        return modifier;
    }
}
