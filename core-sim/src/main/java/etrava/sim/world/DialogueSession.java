package etrava.sim.world;

import etrava.sim.api.DialogueView;
import java.util.ArrayList;
import java.util.List;

/**
 * Conversa em andamento entre o jogador e um NPC. Não vai para o save: não se
 * salva no meio de uma conversa (RF-360).
 */
public final class DialogueSession implements DialogueView {
    public final String dialogueId;
    public final String npcId;
    public final String npcName;
    public final String portrait;
    public String nodeId;
    public String speakerName;
    public String text;
    /** Escolhas visíveis agora. */
    public final List<Choice> choices = new ArrayList<>();
    /** Índices, na definição do nó, das escolhas visíveis. */
    public final List<Integer> choiceIndex = new ArrayList<>();
    public boolean canContinue;
    public final List<Line> history = new ArrayList<>();

    public DialogueSession(String dialogueId, String npcId, String npcName, String portrait) {
        this.dialogueId = dialogueId;
        this.npcId = npcId;
        this.npcName = npcName;
        this.portrait = portrait;
    }

    @Override public String npcId() { return npcId; }
    @Override public String npcName() { return npcName; }
    @Override public String portrait() { return portrait; }
    @Override public String speakerName() { return speakerName; }
    @Override public String text() { return text; }
    @Override public List<Choice> choices() { return choices; }
    @Override public boolean canContinue() { return canContinue; }
    @Override public List<Line> history() { return history; }
}
