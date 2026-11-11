package etrava.sim.content;

import java.util.List;

/**
 * Um contêiner no mapa (content/containers.yaml, RF-204): um cesto, sacos, um baú.
 * Pegar de um contêiner com dono é roubo — se alguém vir (RF-089).
 *
 * @param at          onde fica: "area/ponto"
 * @param owner       NPC dono; null = de ninguém
 * @param items       o que tem no começo, e o que volta a ter na reposição
 * @param restockDays de quantos em quantos dias o dono repõe; 0 = nunca
 */
public record ContainerDef(String id, String name, String at, String owner, List<ItemDef.Stack> items,
        int restockDays) {

    public String displayName() { return name != null ? name : id; }
    public List<ItemDef.Stack> itemList() { return items != null ? items : List.of(); }
    public String area() { return at.substring(0, at.indexOf('/')); }
    public String spot() { return at.substring(at.indexOf('/') + 1); }
    public boolean wellFormed() { return at != null && at.indexOf('/') > 0 && at.indexOf('/') < at.length() - 1; }
}
