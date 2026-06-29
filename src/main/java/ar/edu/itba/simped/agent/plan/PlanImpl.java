package ar.edu.itba.simped.agent.plan;

import ar.edu.itba.simped.core.Task;
import ar.edu.itba.simped.core.ports.Plan;
import ar.edu.itba.simped.core.ports.TaskTarget;

import java.util.List;

/**
 * Plan ordenado de tareas, una instancia por agente (4.2).
 * Implementación original del Grupo 2, adaptada al port.
 */
public final class PlanImpl implements Plan {

    private final List<Task> tasks;
    private int currentTaskIndex = 0;

    public PlanImpl(List<Task> tasks) {
        this.tasks = List.copyOf(tasks);
    }

    @Override
    public List<TaskTarget> taskList() {
        return List.copyOf(tasks);
    }

    @Override
    public int currentTaskIndex() {
        return currentTaskIndex;
    }

    @Override
    public void advance() {
        currentTaskIndex++;
    }

    public Task currentTask() {
        return isComplete() ? null : tasks.get(currentTaskIndex);
    }

    public boolean isComplete() {
        return currentTaskIndex >= tasks.size();
    }

    public int size() {
        return tasks.size();
    }
}
