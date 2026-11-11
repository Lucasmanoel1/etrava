package etrava.sim.content;

import java.util.List;

/**
 * As habilidades (content/skills.yaml, RF-180–182): de 0 a 100, sobem pelo uso,
 * cada vez mais devagar. Não existe nível global nem experiência genérica.
 *
 * @param curve expoente do ganho decrescente: ganho = base × (1 − valor/100)^curve
 */
public record SkillsDef(double curve, List<Skill> skills) {

    public record Skill(String id, String name, String description) {
        public String displayName() { return name != null ? name : id; }
    }

    public static final SkillsDef EMPTY = new SkillsDef(1.5, List.of());

    public List<Skill> list() { return skills != null ? skills : List.of(); }
    public double curveExponent() { return curve > 0 ? curve : 1.5; }

    public Skill skill(String id) {
        for (Skill s : list()) if (s.id().equals(id)) return s;
        return null;
    }
}
