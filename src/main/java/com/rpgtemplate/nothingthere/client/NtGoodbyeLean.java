package com.rpgtemplate.nothingthere.client;

import com.rpgtemplate.nothingthere.NtBody;
import java.util.WeakHashMap;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Torso lean and crouch for the goodbye strike (video, first 3 s). Applied after vanilla setupAnim, so the
 * vanilla walk and idle motion stay underneath. The hip is the pivot: every upper part (head, body, arms) rotates
 * about it, and the hands follow because they hang from the arm parts.
 *
 * A PlayerModel is shared by every player drawn with the same renderer, so each model remembers what it changed
 * last frame and undoes it at HEAD. The base pivots are captured the first time a model is seen unchanged.
 */
public final class NtGoodbyeLean {
    /** Hip joint height in model pixels (legs are 12 px long, the pivot sits at y = 12). */
    private static final float HIP_Y = 12f;
    private static final int PARTS = 12;

    private static final WeakHashMap<PlayerModel<?>, State> STATES = new WeakHashMap<>();

    private NtGoodbyeLean() {
    }

    private static final class State {
        final ModelPart[] parts = new ModelPart[PARTS];
        final float[][] base = new float[PARTS][3];
        final float[] applied = new float[PARTS];
        boolean captured;
        boolean dirty;
    }

    /** HEAD of setupAnim: undo last frame's lean so vanilla starts from its own pose. */
    public static void restore(PlayerModel<?> model) {
        State s = STATES.get(model);
        if (s == null) {
            s = new State();
            fill(model, s);
            STATES.put(model, s);
        }
        if (!s.captured) {
            for (int i = 0; i < PARTS; i++) {
                s.base[i][0] = s.parts[i].x;
                s.base[i][1] = s.parts[i].y;
                s.base[i][2] = s.parts[i].z;
            }
            s.captured = true;
        }
        if (!s.dirty) {
            return;
        }
        for (int i = 0; i < PARTS; i++) {
            s.parts[i].xRot -= s.applied[i];
            s.applied[i] = 0f;
            s.parts[i].x = s.base[i][0];
            s.parts[i].y = s.base[i][1];
            s.parts[i].z = s.base[i][2];
        }
        s.dirty = false;
    }

    /** TAIL of setupAnim: apply the lean for this entity, if it is in the goodbye strike. */
    public static void apply(PlayerModel<?> model, LivingEntity entity, float ageInTicks) {
        if (!(entity instanceof AbstractClientPlayer player) || !NtBody.isHuman(player)) {
            return;
        }
        State s = STATES.get(model);
        if (s == null) {
            return;
        }
        float partial = ageInTicks - (float) Math.floor(ageInTicks);
        float now = (float) player.level().getGameTime() + partial;
        boolean flag = NtBody.goodbyePending(player);
        NtGoodbyeAnim.observe(player, flag, now);
        float t = NtGoodbyeAnim.elapsed(player, now);
        float lean;
        float crouch;
        float front;
        float back;
        if (t >= 0f) {
            float f = NtGoodbyeAnim.leanFraction(t);
            lean = NtGoodbyeAnim.LEAN_MAX_RAD * f;
            crouch = NtGoodbyeAnim.CROUCH_PX * f;
            front = NtGoodbyeAnim.FRONT_LEG_RAD * f;
            back = NtGoodbyeAnim.BACK_LEG_RAD * f;
        } else {
            // Holle cast: no torso lean, the legs take the kneel pose (NtHolleAnim).
            boolean holleFlag = NtBody.hollePending(player);
            NtHolleAnim.observe(player, holleFlag, now);
            float h = NtHolleAnim.elapsed(player, now);
            if (h < 0f) {
                return;
            }
            float f = NtHolleAnim.legFraction(h);
            lean = 0f;
            crouch = NtHolleAnim.CROUCH_PX * f;
            front = NtHolleAnim.FRONT_LEG_RAD * f;
            back = NtHolleAnim.BACK_LEG_RAD * f;
        }
        float cos = (float) Math.cos(lean);
        float sin = (float) Math.sin(lean);
        for (int i = 0; i < PARTS; i++) {
            float x = s.base[i][0];
            float y = s.base[i][1];
            float dy = y - HIP_Y;
            float dz = s.base[i][2];
            ModelPart part = s.parts[i];
            part.x = x;
            s.applied[i] = 0f;
            if (y == HIP_Y) {
                // legs and leg overlays hang from the hip: only lowered, then swung front/back
                part.y = y + crouch;
                part.z = dz;
                float swing = x < 0f ? front : back;
                part.xRot += swing;
                s.applied[i] = swing;
            } else {
                part.y = HIP_Y + dy * cos - dz * sin + crouch;
                part.z = dy * sin + dz * cos;
                part.xRot += lean;
                s.applied[i] = lean;
            }
        }
        s.dirty = true;
    }

    /** Torso lean in radians for elapsed tick t: the layer subtracts it so the blade arm keeps the video angle. */
    public static float leanRad(float t) {
        return NtGoodbyeAnim.LEAN_MAX_RAD * NtGoodbyeAnim.leanFraction(t);
    }

    /** Fixed order of the 12 upper and leg parts of PlayerModel (the same set NtBodyClient hides for a cocoon). */
    private static void fill(PlayerModel<?> m, State s) {
        s.parts[0] = m.rightArm;
        s.parts[1] = m.leftArm;
        s.parts[2] = m.leftSleeve;
        s.parts[3] = m.rightSleeve;
        s.parts[4] = m.head;
        s.parts[5] = m.rightLeg;
        s.parts[6] = m.leftLeg;
        s.parts[7] = m.hat;
        s.parts[8] = m.body;
        s.parts[9] = m.leftPants;
        s.parts[10] = m.rightPants;
        s.parts[11] = m.jacket;
    }
}
