package ar.edu.itba.simped.input.json;

import java.util.List;

public record PlanJson(
        String name,
        String exitSelection,
        List<ObjectiveGroupJson> objectiveGroups) {
}
