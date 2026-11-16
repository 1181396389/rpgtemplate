package com.rpgtemplate.cronus.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * 假面骑士 Cronus 高精度像素风 3D 变身装甲与腰带模型（128x128 逻辑 UV 映射 256x256 双倍高清像素贴图）。
 * <p>严格忠实还原假面骑士 Cronus 外观：后掠锯齿黑蓝绿头冠 + 两侧荧光绿耳翼 + Ex-Aid 螺旋复眼 + 胸口四点编年史仪表与电路纹 +
 * 双肩高耸翼状肩甲与钩状绿刃 + 腰间故障驱动器Ⅱ（含红/青发光按键与编年史卡带插槽） + 银边前开叉三片式动态长风衣摆 + 护胫战靴。</p>
 * <p>同时支持：全套变身外观渲染、人形态单独佩戴【故障驱动器Ⅱ】腰带渲染、以及第一人称装甲手臂渲染。</p>
 */
public final class CronusSuitModel {
    private static final ResourceLocation SUIT_TEX =
        new ResourceLocation("rpgtemplate", "textures/entity/cronus_suit.png");
    private static final ResourceLocation SUIT_EMISSIVE_TEX =
        new ResourceLocation("rpgtemplate", "textures/entity/cronus_suit_emissive.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    private static CronusSuitModel instance;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart body;
    private final ModelPart beltGroup;
    private final ModelPart beltGashat;
    private final ModelPart coatBack;
    private final ModelPart coatRight;
    private final ModelPart coatLeft;
    private final ModelPart rightArm;
    private final ModelPart rightPauldron;
    private final ModelPart leftArm;
    private final ModelPart leftPauldron;
    private final ModelPart rightLeg;
    private final ModelPart leftLeg;

    private CronusSuitModel() {
        this.root = createLayerDefinition().m_171564_();
        this.head = root.m_171324_("head");
        this.body = root.m_171324_("body");
        this.beltGroup = body.m_171324_("belt_group");
        this.beltGashat = beltGroup.m_171324_("driver_gashat");
        this.coatBack = body.m_171324_("coat_back");
        this.coatRight = body.m_171324_("coat_right");
        this.coatLeft = body.m_171324_("coat_left");
        this.rightArm = root.m_171324_("right_arm");
        this.rightPauldron = rightArm.m_171324_("pauldron_r");
        this.leftArm = root.m_171324_("left_arm");
        this.leftPauldron = leftArm.m_171324_("pauldron_l");
        this.rightLeg = root.m_171324_("right_leg");
        this.leftLeg = root.m_171324_("left_leg");
    }

    public static CronusSuitModel get() {
        if (instance == null) {
            instance = new CronusSuitModel();
        }
        return instance;
    }

    private static LayerDefinition createLayerDefinition() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.m_171576_();

        // ==================== 1. HEAD（头盔、螺旋复眼、V型眉甲、下颌护盾、三层后掠锯齿头冠、双耳翼） ====================
        PartDefinition head = root.m_171599_("head",
            CubeListBuilder.m_171558_()
                // 头盔主体（包裹原版头部，零穿模）
                .m_171514_(0, 0).m_171488_(-4.0f, -8.0f, -4.0f, 8.0f, 8.0f, 8.0f, new CubeDeformation(0.42f))
                // 眉骨护额（精准位于复眼上方，不遮眼）
                .m_171514_(32, 0).m_171488_(-4.5f, -8.35f, -4.75f, 9.0f, 2.0f, 1.0f, new CubeDeformation(0.08f))
                // 尖锐下颌面罩（^-形荧光绿颌线）
                .m_171514_(32, 5).m_171488_(-3.0f, -1.75f, -4.65f, 6.0f, 2.0f, 2.0f, new CubeDeformation(-0.02f)),
            PartPose.f_171404_);

        // 三段式标志性后掠锯齿尖刺头冠
        head.m_171599_("crest_front",
            CubeListBuilder.m_171558_().m_171514_(54, 0)
                .m_171488_(-0.85f, -6.2f, -1.6f, 2.0f, 7.0f, 5.0f, new CubeDeformation(-0.15f)),
            PartPose.m_171423_(0.0f, -7.6f, -2.5f, -0.34f, 0.0f, 0.0f));
        head.m_171599_("crest_mid",
            CubeListBuilder.m_171558_().m_171514_(54, 0)
                .m_171488_(-0.95f, -6.8f, -1.2f, 2.0f, 7.0f, 5.0f, new CubeDeformation(-0.05f)),
            PartPose.m_171423_(0.0f, -7.8f, 0.2f, -0.56f, 0.0f, 0.0f));
        head.m_171599_("crest_back",
            CubeListBuilder.m_171558_().m_171514_(54, 0)
                .m_171488_(-0.85f, -5.6f, -1.0f, 2.0f, 7.0f, 5.0f, new CubeDeformation(-0.20f)),
            PartPose.m_171423_(0.0f, -6.2f, 2.8f, -0.82f, 0.0f, 0.0f));

        // 两侧斜后发刺翼片
        head.m_171599_("crest_r",
            CubeListBuilder.m_171558_().m_171514_(70, 0)
                .m_171481_(-0.5f, -5.2f, -1.5f, 1.0f, 6.0f, 4.0f),
            PartPose.m_171423_(-2.4f, -7.4f, -0.8f, -0.42f, 0.0f, -0.26f));
        head.m_171599_("crest_l",
            CubeListBuilder.m_171558_().m_171514_(70, 0)
                .m_171481_(-0.5f, -5.2f, -1.5f, 1.0f, 6.0f, 4.0f),
            PartPose.m_171423_(2.4f, -7.4f, -0.8f, -0.42f, 0.0f, 0.26f));

        // 标志性荧光绿斜上耳翼尖角
        head.m_171599_("ear_r",
            CubeListBuilder.m_171558_().m_171514_(82, 0)
                .m_171488_(-0.8f, -5.8f, -1.3f, 1.0f, 6.0f, 3.0f, new CubeDeformation(0.05f)),
            PartPose.m_171423_(-4.4f, -5.0f, -0.6f, -0.18f, 0.0f, -0.34f));
        head.m_171599_("ear_l",
            CubeListBuilder.m_171558_().m_171514_(82, 0)
                .m_171488_(-0.2f, -5.8f, -1.3f, 1.0f, 6.0f, 3.0f, new CubeDeformation(0.05f)),
            PartPose.m_171423_(4.4f, -5.0f, -0.6f, -0.18f, 0.0f, 0.34f));

        // ==================== 2. BODY（胸甲、四点仪表、绿色电路、腰带故障驱动器Ⅱ、三片式动态长风衣） ====================
        PartDefinition body = root.m_171599_("body",
            CubeListBuilder.m_171558_()
                .m_171514_(0, 18).m_171488_(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, new CubeDeformation(0.30f))
                .m_171514_(26, 18).m_171488_(-4.5f, 0.3f, -2.75f, 9.0f, 6.0f, 6.0f, new CubeDeformation(0.12f))
                .m_171514_(92, 0).m_171488_(-3.8f, -1.3f, -2.4f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.20f))
                .m_171514_(92, 0).m_171488_(1.8f, -1.3f, -2.4f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.20f))
                .m_171514_(102, 0).m_171488_(-4.6f, 5.6f, -2.1f, 2.0f, 4.0f, 3.0f, new CubeDeformation(0.05f))
                .m_171514_(102, 0).m_171488_(2.6f, 5.6f, -2.1f, 2.0f, 4.0f, 3.0f, new CubeDeformation(0.05f)),
            PartPose.f_171404_);

        // 故障驱动器Ⅱ 3D 独立腰带组（支持未变身人形态单独佩戴渲染！）
        PartDefinition belt = body.m_171599_("belt_group",
            CubeListBuilder.m_171558_()
                .m_171514_(58, 30).m_171488_(-4.5f, 9.6f, -2.5f, 9.0f, 2.0f, 5.0f, new CubeDeformation(0.15f))
                .m_171514_(100, 18).m_171488_(-3.0f, 9.2f, -3.65f, 6.0f, 3.0f, 2.0f, new CubeDeformation(-0.10f))
                .m_171514_(100, 25).m_171488_(-2.8f, 9.7f, -4.10f, 2.0f, 2.0f, 1.0f, new CubeDeformation(-0.15f))
                .m_171514_(108, 25).m_171488_(0.8f, 9.7f, -4.10f, 2.0f, 2.0f, 1.0f, new CubeDeformation(-0.15f))
                .m_171514_(116, 25).m_171481_(2.7f, 10.1f, -3.55f, 2.0f, 1.0f, 1.0f),
            PartPose.f_171404_);

        // 中央插入态《假面骑士编年史》转玩卡带（未插卡/未变身时隐藏，变身后亮起显现！）
        belt.m_171599_("driver_gashat",
            CubeListBuilder.m_171558_().m_171514_(100, 30)
                .m_171488_(-1.0f, 7.6f, -3.95f, 2.0f, 4.0f, 1.0f, new CubeDeformation(-0.08f)),
            PartPose.f_171404_);

        // 标志性银边前开叉黑色绿纹长风衣（后摆 + 右摆 + 左摆，行走奔跑动态飘动）
        body.m_171599_("coat_back",
            CubeListBuilder.m_171558_().m_171514_(0, 60)
                .m_171488_(-5.0f, 0.0f, -0.3f, 10.0f, 11.0f, 1.0f, new CubeDeformation(0.05f)),
            PartPose.m_171423_(0.0f, 11.2f, 2.3f, 0.14f, 0.0f, 0.0f));
        body.m_171599_("coat_right",
            CubeListBuilder.m_171558_().m_171514_(24, 60)
                .m_171488_(-2.6f, 0.0f, -2.6f, 4.0f, 11.0f, 5.0f, new CubeDeformation(0.12f)),
            PartPose.m_171423_(-2.4f, 11.2f, 0.0f, 0.04f, 0.0f, 0.16f));
        body.m_171599_("coat_left",
            CubeListBuilder.m_171558_().m_171514_(44, 60)
                .m_171488_(-1.4f, 0.0f, -2.6f, 4.0f, 11.0f, 5.0f, new CubeDeformation(0.12f)),
            PartPose.m_171423_(2.4f, 11.2f, 0.0f, 0.04f, 0.0f, -0.16f));

        // ==================== 3. RIGHT & LEFT ARMS（全装甲手臂 + 标志性翼状肩甲与钩状竖刃） ====================
        PartDefinition rightArm = root.m_171599_("right_arm",
            CubeListBuilder.m_171558_().m_171514_(0, 40)
                .m_171488_(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.30f)),
            PartPose.m_171419_(-5.0f, 2.0f, 0.0f));
        PartDefinition pauldronR = rightArm.m_171599_("pauldron_r",
            CubeListBuilder.m_171558_().m_171514_(58, 18)
                .m_171488_(-3.4f, -1.4f, -3.0f, 6.0f, 4.0f, 6.0f, new CubeDeformation(0.05f)),
            PartPose.m_171423_(-1.5f, -1.8f, 0.0f, 0.0f, 0.0f, 0.16f));
        pauldronR.m_171599_("horn_r",
            CubeListBuilder.m_171558_().m_171514_(84, 18)
                .m_171488_(-0.7f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f, new CubeDeformation(0.05f)),
            PartPose.m_171423_(-3.0f, -1.2f, 0.0f, 0.0f, 0.0f, -0.22f));
        pauldronR.m_171599_("blade_r",
            CubeListBuilder.m_171558_().m_171514_(92, 18)
                .m_171481_(-0.6f, 0.0f, -1.0f, 1.0f, 3.0f, 2.0f),
            PartPose.m_171423_(-3.0f, 2.2f, 0.0f, 0.0f, 0.0f, 0.20f));

        PartDefinition leftArm = root.m_171599_("left_arm",
            CubeListBuilder.m_171558_().m_171514_(18, 40)
                .m_171488_(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.30f)),
            PartPose.m_171419_(5.0f, 2.0f, 0.0f));
        PartDefinition pauldronL = leftArm.m_171599_("pauldron_l",
            CubeListBuilder.m_171558_().m_171514_(58, 18)
                .m_171488_(-2.6f, -1.4f, -3.0f, 6.0f, 4.0f, 6.0f, new CubeDeformation(0.05f)),
            PartPose.m_171423_(1.5f, -1.8f, 0.0f, 0.0f, 0.0f, -0.16f));
        pauldronL.m_171599_("horn_l",
            CubeListBuilder.m_171558_().m_171514_(84, 18)
                .m_171488_(-0.3f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f, new CubeDeformation(0.05f)),
            PartPose.m_171423_(3.0f, -1.2f, 0.0f, 0.0f, 0.0f, 0.22f));
        pauldronL.m_171599_("blade_l",
            CubeListBuilder.m_171558_().m_171514_(92, 18)
                .m_171481_(-0.4f, 0.0f, -1.0f, 1.0f, 3.0f, 2.0f),
            PartPose.m_171423_(3.0f, 2.2f, 0.0f, 0.0f, 0.0f, -0.20f));

        // ==================== 4. RIGHT & LEFT LEGS（黑金底裤 + V型绿边护胫与战靴） ====================
        root.m_171599_("right_leg",
            CubeListBuilder.m_171558_().m_171514_(36, 40)
                .m_171488_(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.28f)),
            PartPose.m_171419_(-1.9f, 12.0f, 0.0f));
        root.m_171599_("left_leg",
            CubeListBuilder.m_171558_().m_171514_(54, 40)
                .m_171488_(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.28f)),
            PartPose.m_171419_(1.9f, 12.0f, 0.0f));

        return LayerDefinition.m_171565_(mesh, 128, 128);
    }

    /** 渲染完整变身态假面骑士 Cronus 3D 装甲外观（含常态受光层 + 夜间全亮自发光层 + 动态风衣摆）。 */
    public void renderFullSuit(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               PlayerModel<AbstractClientPlayer> parent, AbstractClientPlayer player,
                               float limbSwing, float limbSwingAmount, float partialTick) {
        this.head.m_104315_(parent.f_102808_);
        this.body.m_104315_(parent.f_102810_);
        this.rightArm.m_104315_(parent.f_102811_);
        this.leftArm.m_104315_(parent.f_102812_);
        this.rightLeg.m_104315_(parent.f_102813_);
        this.leftLeg.m_104315_(parent.f_102814_);

        this.head.f_104207_ = true;
        this.body.f_104207_ = true;
        this.beltGroup.f_104207_ = true;
        this.beltGashat.f_104207_ = true; // 变身态腰带永远显示已插入编年史卡带
        this.coatBack.f_104207_ = true;
        this.coatRight.f_104207_ = true;
        this.coatLeft.f_104207_ = true;
        this.rightArm.f_104207_ = true;
        this.rightPauldron.f_104207_ = true;
        this.leftArm.f_104207_ = true;
        this.leftPauldron.f_104207_ = true;
        this.rightLeg.f_104207_ = true;
        this.leftLeg.f_104207_ = true;

        // 动态长风衣摆随步幅、潜行与腾空自然飘扬，避免双腿穿模
        float swingR = parent.f_102813_.f_104203_;
        float swingL = parent.f_102814_.f_104203_;
        float speedFlare = Mth.m_14036_(limbSwingAmount, 0.0f, 1.0f) * 0.24f;
        float airLift = player.m_20096_() ? 0.0f : 0.18f;
        float sneakTilt = player.m_6047_() ? 0.16f : 0.0f;

        this.coatRight.f_104203_ = 0.04f + swingR * 0.62f + sneakTilt * 0.4f;
        this.coatRight.f_104205_ = 0.16f + speedFlare * 0.35f + airLift * 0.4f;
        this.coatLeft.f_104203_ = 0.04f + swingL * 0.62f + sneakTilt * 0.4f;
        this.coatLeft.f_104205_ = -0.16f - speedFlare * 0.35f - airLift * 0.4f;
        this.coatBack.f_104203_ = 0.14f + speedFlare + airLift + sneakTilt * 0.6f
            + Math.max(0.0f, Math.max(swingR, swingL) * 0.35f);

        // Pass 1：受世界光照的本体高清像素风装甲（双面无剔除，风衣/耳翼/头冠无背面穿帮）
        VertexConsumer baseVc = buffers.m_6299_(RenderType.m_110458_(SUIT_TEX));
        this.root.m_104301_(pose, baseVc, light, overlay);

        // Pass 2：全亮自发光层（荧光绿螺旋眼、胸口四点仪表与电路纹、驱动器红青灯、绿刃光边）
        VertexConsumer emisVc = buffers.m_6299_(RenderType.m_110488_(SUIT_EMISSIVE_TEX));
        this.root.m_104306_(pose, emisVc, FULL_BRIGHT, overlay, 0.92f, 0.96f, 0.92f, 0.95f);
    }

    /** 人形态单独佩戴【故障驱动器Ⅱ】腰带时的 3D 腰部渲染（随躯干俯仰/潜行精确贴合）。 */
    public void renderBeltOnly(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               PlayerModel<AbstractClientPlayer> parent, boolean showGashat) {
        pose.m_85836_();
        parent.f_102810_.m_104299_(pose);
        this.beltGroup.f_104207_ = true;
        this.beltGashat.f_104207_ = showGashat;

        VertexConsumer baseVc = buffers.m_6299_(RenderType.m_110458_(SUIT_TEX));
        this.beltGroup.m_104301_(pose, baseVc, light, overlay);

        VertexConsumer emisVc = buffers.m_6299_(RenderType.m_110488_(SUIT_EMISSIVE_TEX));
        this.beltGroup.m_104306_(pose, emisVc, FULL_BRIGHT, overlay, 0.95f, 0.95f, 0.95f, 0.95f);
        pose.m_85849_();
    }

    /** 第一人称空手手臂渲染：仅渲染黑绿护臂拳套（隐藏肩甲避免遮挡第一人称视野）。 */
    public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                     HumanoidArm arm) {
        ModelPart part = (arm == HumanoidArm.RIGHT) ? this.rightArm : this.leftArm;
        ModelPart pauldron = (arm == HumanoidArm.RIGHT) ? this.rightPauldron : this.leftPauldron;

        float sx = part.f_104200_, sy = part.f_104201_, sz = part.f_104202_;
        float rx = part.f_104203_, ry = part.f_104204_, rz = part.f_104205_;
        boolean prevPauldron = pauldron.f_104207_;
        try {
            part.f_104200_ = (arm == HumanoidArm.RIGHT) ? -5.0f : 5.0f;
            part.f_104201_ = 2.0f;
            part.f_104202_ = 0.0f;
            part.f_104203_ = 0.0f;
            part.f_104204_ = 0.0f;
            part.f_104205_ = 0.0f;
            pauldron.f_104207_ = false;

            VertexConsumer baseVc = buffers.m_6299_(RenderType.m_110458_(SUIT_TEX));
            part.m_104301_(pose, baseVc, light, overlay);

            VertexConsumer emisVc = buffers.m_6299_(RenderType.m_110488_(SUIT_EMISSIVE_TEX));
            part.m_104306_(pose, emisVc, FULL_BRIGHT, overlay, 0.90f, 0.95f, 0.90f, 0.92f);
        } finally {
            part.f_104200_ = sx;
            part.f_104201_ = sy;
            part.f_104202_ = sz;
            part.f_104203_ = rx;
            part.f_104204_ = ry;
            part.f_104205_ = rz;
            pauldron.f_104207_ = prevPauldron;
        }
    }
}
