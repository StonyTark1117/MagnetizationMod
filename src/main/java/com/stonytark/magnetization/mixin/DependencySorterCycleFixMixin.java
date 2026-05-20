package com.stonytark.magnetization.mixin;

import com.google.common.collect.Multimap;
import net.minecraft.util.DependencySorter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Unique;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Vanilla's {@link DependencySorter#isCyclic} recursively walks a dependency multimap with no
 * visited-set memoization. If the multimap already contains a self-loop (node X → X), every
 * subsequent recursion into X re-streams X's collection and re-recurses, blowing the stack.
 *
 * <p>A self-loop can get inserted in the first place because the initial call to
 * {@code addDependencyIfNotCyclic(map, X, X)} runs isCyclic against an empty map, finds no
 * cycle, and writes the X→X edge. From that point on the map is poisoned.
 *
 * <p>Real-world trigger: {@code spartan_weaponry_unofficial} ships a self-referencing
 * {@code data/c/tags/item/ingots/aluminum.json} that lists {@code #c:ingots/aluminum} as a
 * child of itself. Any other mod referencing {@code #c:ingots/aluminum} (Magnetization being
 * one of many) triggers a StackOverflowError at datapack load — preventing world creation on
 * any modpack that combines spartan + accessories + accessories_compat_layer + a metal-tag-
 * consuming mod.
 *
 * <p>This mixin replaces isCyclic with an iterative-friendly version that carries a HashSet
 * of nodes it's already visited in the current check. Hitting a visited node short-circuits
 * to false (no cycle through this path), preserving vanilla's contract: <em>"would adding
 * edge p_target → p_current create a NEW cycle by reaching back to target?"</em>
 *
 * <p>If a real cycle exists, the visited-set still catches it via the {@code collection.contains(target)}
 * direct check on each node. The only behavior change is that pre-existing self-loops no longer
 * blow the stack; they simply terminate the recursion when re-encountered, which is the correct
 * semantics.
 *
 * <p>This is a vanilla bug worth filing upstream; remove this mixin once NeoForge or Mojang ships
 * the fix.
 */
@Mixin(DependencySorter.class)
public class DependencySorterCycleFixMixin {

    /**
     * Overrides {@link DependencySorter#isCyclic} with a memoized version. Vanilla signature:
     * {@code private static <K> boolean isCyclic(Multimap<K, K>, K target, K current)}.
     */
    @Overwrite
    private static <K> boolean isCyclic(Multimap<K, K> multimap, K target, K current) {
        return magnetization$isCyclicMemoized(multimap, target, current, new HashSet<>());
    }

    @Unique
    private static <K> boolean magnetization$isCyclicMemoized(
            Multimap<K, K> multimap, K target, K current, Set<K> visited) {
        if (!visited.add(current)) {
            return false;
        }
        Collection<K> collection = multimap.get(current);
        if (collection.contains(target)) {
            return true;
        }
        for (K next : collection) {
            if (magnetization$isCyclicMemoized(multimap, target, next, visited)) {
                return true;
            }
        }
        return false;
    }
}
