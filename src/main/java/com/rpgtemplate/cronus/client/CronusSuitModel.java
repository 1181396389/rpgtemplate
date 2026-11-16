package com.rpgtemplate.cronus.client;
import net.minecraft.util.FastColor;

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
 * 假面骑士 Cronus 超精细 3D 变身装甲与腰带模型（256x256 逻辑 UV 映射 512x512 超高清像素贴图）。
 * <ul>
 *   <li><b>头部精细化（0.40x 亚体素实体精雕，28 实体部件，参照 S.H.Figuarts 实拍特写）</b>：
 *       圆顶分段收窄式骑士头盔轮廓、内嵌熏黑 Ex-Aid 螺旋复眼护目镜（36x18 超清同心翠绿螺旋纹）、
 *       立体金属黄绿 V 型眉甲与斜切侧颊甲、冷峻黑曜倒 V 呼吸面罩 + 尖锐金属绿 V 型下巴护盾、
 *       多段实体弯曲后掠 Ex-Aid 皇室蓝黑/金属绿主发刺冠与前额 V 刃、以及三段阶梯实体闪电耳翼。</li>
 *   <li><b>腰带精细化（0.34x 亚体素精雕，28 部件忠实还原 DX 故障驱动器Ⅱ）</b>：高规格黑纹腰带与后扣、
 *       八角切面银钛外框 + 金属翡翠青外壳、中央八角全息屏幕（变身点亮假面骑士 Cronus 绿光剪影；未变身为熏黑待机玻璃）、
 *       左翼圆形浮雕红「A」键与双枪口喷嘴、右翼圆形浮雕青「B」键与锯齿链锯刀刃、顶部斜向插卡桥（含青色 LED、红色锁扣、青色三角饰块）
 *       以及斜插镂空握把《假面骑士编年史》转玩卡带！</li>
 * </ul>
 */
public final class CronusSuitModel {
    private static final ResourceLocation SUIT_TEX =
        ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/entity/cronus_suit.png");
    private static final ResourceLocation SUIT_EMISSIVE_TEX =
        ResourceLocation.fromNamespaceAndPath("rpgtemplate", "textures/entity/cronus_suit_emissive.png");
    private static final int FULL_BRIGHT = 0x00F000F0;

    private static CronusSuitModel instance;

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart headHd;
    private final ModelPart body;
    private final ModelPart beltGroup;
    private final ModelPart beltGashatGroup;
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
        this.root = createLayerDefinition().bakeRoot();
        this.head = root.getChild("head");
        this.headHd = head.getChild("head_hd");
        this.headHd.xScale = 0.40f;
        this.headHd.yScale = 0.40f;
        this.headHd.zScale = 0.40f;

        this.body = root.getChild("body");
        this.beltGroup = body.getChild("belt_group");
        this.beltGroup.xScale = 0.34f;
        this.beltGroup.yScale = 0.34f;
        this.beltGroup.zScale = 0.34f;
        this.beltGashatGroup = beltGroup.getChild("driver_gashat_group");

        this.coatBack = body.getChild("coat_back");
        this.coatRight = body.getChild("coat_right");
        this.coatLeft = body.getChild("coat_left");
        this.rightArm = root.getChild("right_arm");
        this.rightPauldron = rightArm.getChild("pauldron_r");
        this.leftArm = root.getChild("left_arm");
        this.leftPauldron = leftArm.getChild("pauldron_l");
        this.rightLeg = root.getChild("right_leg");
        this.leftLeg = root.getChild("left_leg");
    }

    public static CronusSuitModel get() {
        if (instance == null) {
            instance = new CronusSuitModel();
        }
        return instance;
    }

    private static LayerDefinition createLayerDefinition() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // ==================== 1. HEAD（0.40x 亚体素实体精雕 S.H.Figuarts 级 Cronus 头盔，零空心薄片感） ====================
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition headHd = head.addOrReplaceChild("head_hd",
            CubeListBuilder.create()
                // 上部圆弧盔顶 + 阶梯收窄顶冠 + 窄收下颌内芯（塑造真正收窄式骑士头盔剪影而非方块！）
                .texOffs(0, 200).addBox(-9.0f, -18.8f, -9.0f, 18.0f, 12.0f, 18.0f, new CubeDeformation(0.25f))
                .texOffs(74, 200).addBox(-7.5f, -21.2f, -8.0f, 15.0f, 3.0f, 16.0f, new CubeDeformation(0.20f))
                .texOffs(138, 200).addBox(-7.0f, -6.8f, -7.8f, 14.0f, 7.0f, 15.0f, new CubeDeformation(0.20f))
                // 内嵌式熏黑 Ex-Aid 复眼护目镜（36x18 超清同心翠绿螺旋纹）+ 冷峻黑曜倒 V 呼吸面罩
                .texOffs(0, 232).addBox(-9.0f, -15.4f, -10.0f, 18.0f, 9.0f, 2.0f)
                .texOffs(44, 232).addBox(-7.0f, -7.0f, -10.4f, 14.0f, 6.0f, 3.0f),
            PartPose.ZERO);

        // 立体金属黄绿 V 型眉骨护额（悬挑覆盖熏黑护目镜上沿）
        headHd.addOrReplaceChild("h_brow_r",
            CubeListBuilder.create().texOffs(108, 232).addBox(-5.0f, -1.0f, -1.0f, 10.0f, 2.0f, 2.0f),
            PartPose.offsetAndRotation(-5.4f, -15.2f, -10.6f, 0.12f, 0.16f, 0.34f));
        headHd.addOrReplaceChild("h_brow_l",
            CubeListBuilder.create().texOffs(108, 232).addBox(-5.0f, -1.0f, -1.0f, 10.0f, 2.0f, 2.0f),
            PartPose.offsetAndRotation(5.4f, -15.2f, -10.6f, 0.12f, -0.16f, -0.34f));

        // 立体实体斜切侧颊甲 + 金属黄绿尖锐 V 型下巴护盾
        headHd.addOrReplaceChild("h_cheek_r",
            CubeListBuilder.create().texOffs(136, 232).addBox(-1.5f, -3.5f, -3.0f, 3.0f, 7.0f, 5.0f),
            PartPose.offsetAndRotation(-8.4f, -5.2f, -6.2f, 0.10f, 0.24f, -0.22f));
        headHd.addOrReplaceChild("h_cheek_l",
            CubeListBuilder.create().texOffs(136, 232).addBox(-1.5f, -3.5f, -3.0f, 3.0f, 7.0f, 5.0f),
            PartPose.offsetAndRotation(8.4f, -5.2f, -6.2f, 0.10f, -0.24f, 0.22f));
        headHd.addOrReplaceChild("h_chin_shield",
            CubeListBuilder.create().texOffs(82, 232).addBox(-4.0f, -1.8f, -1.5f, 8.0f, 4.0f, 3.0f),
            PartPose.offsetAndRotation(0.0f, -1.4f, -10.2f, -0.26f, 0.0f, 0.0f));

        // 中央前额 V 刃（2 段实体棱锥）
        PartDefinition hairVCenter = headHd.addOrReplaceChild("hair_v_center",
            CubeListBuilder.create().texOffs(0, 84).addBox(-1.5f, -6.5f, -1.5f, 3.0f, 7.0f, 3.0f),
            PartPose.offsetAndRotation(0.0f, -15.0f, -10.8f, -0.16f, 0.0f, 0.0f));
        hairVCenter.addOrReplaceChild("hair_v_tip",
            CubeListBuilder.create().texOffs(14, 84).addBox(-1.0f, -4.8f, -1.0f, 2.0f, 5.0f, 2.0f),
            PartPose.offsetAndRotation(0.0f, -6.2f, -0.2f, -0.14f, 0.0f, 0.0f));

        // 两侧斜飞前额刘海发刺（2 段实体棱锥）
        PartDefinition hairBangLeft = headHd.addOrReplaceChild("hair_bang_left",
            CubeListBuilder.create().texOffs(126, 84).addBox(-1.5f, -6.5f, -2.0f, 3.0f, 7.0f, 4.0f),
            PartPose.offsetAndRotation(-3.6f, -18.0f, -8.8f, -0.18f, 0.14f, -0.48f));
        hairBangLeft.addOrReplaceChild("hair_bang_left_t",
            CubeListBuilder.create().texOffs(144, 84).addBox(-1.0f, -4.8f, -1.0f, 2.0f, 5.0f, 2.0f),
            PartPose.offsetAndRotation(0.0f, -6.2f, 0.2f, -0.18f, 0.0f, -0.14f));
        PartDefinition hairBangRight = headHd.addOrReplaceChild("hair_bang_right",
            CubeListBuilder.create().texOffs(126, 84).addBox(-1.5f, -6.8f, -2.0f, 3.0f, 7.0f, 4.0f),
            PartPose.offsetAndRotation(3.8f, -18.2f, -8.6f, -0.18f, -0.14f, 0.52f));
        hairBangRight.addOrReplaceChild("hair_bang_right_t",
            CubeListBuilder.create().texOffs(144, 84).addBox(-1.0f, -4.8f, -1.0f, 2.0f, 5.0f, 2.0f),
            PartPose.offsetAndRotation(0.0f, -6.5f, 0.2f, -0.18f, 0.0f, 0.16f));

        // 标志性不对称向右上弯曲后掠 Ex-Aid 镰刀主发刺冠（3 段实体弧形渐变棱锥，带金属绿前刃与皇室蓝黑背面）
        PartDefinition hairSweep1 = headHd.addOrReplaceChild("hair_sweep_1",
            CubeListBuilder.create().texOffs(24, 84).addBox(-2.5f, -7.5f, -3.5f, 5.0f, 8.0f, 7.0f),
            PartPose.offsetAndRotation(-2.0f, -20.0f, -5.6f, -0.24f, 0.0f, 0.34f));
        PartDefinition hairSweep1m = hairSweep1.addOrReplaceChild("hair_sweep_1m",
            CubeListBuilder.create().texOffs(52, 84).addBox(-2.0f, -7.5f, -2.5f, 4.0f, 8.0f, 5.0f),
            PartPose.offsetAndRotation(0.3f, -7.2f, 0.6f, -0.22f, 0.0f, 0.24f));
        hairSweep1m.addOrReplaceChild("hair_sweep_1t",
            CubeListBuilder.create().texOffs(74, 84).addBox(-1.0f, -5.6f, -1.5f, 2.0f, 6.0f, 3.0f),
            PartPose.offsetAndRotation(0.3f, -7.2f, 0.6f, -0.22f, 0.0f, 0.22f));

        // 次级层叠后掠发刺 + 侧翼后掠发刺 + 后脑气动脊刃（均为 2 段实体弯曲棱锥）
        PartDefinition hairSweep2 = headHd.addOrReplaceChild("hair_sweep_2",
            CubeListBuilder.create().texOffs(88, 84).addBox(-2.0f, -8.4f, -3.0f, 4.0f, 9.0f, 6.0f),
            PartPose.offsetAndRotation(1.2f, -19.6f, -1.4f, -0.38f, 0.0f, 0.56f));
        hairSweep2.addOrReplaceChild("hair_sweep_2t",
            CubeListBuilder.create().texOffs(112, 84).addBox(-1.0f, -5.6f, -1.5f, 2.0f, 6.0f, 3.0f),
            PartPose.offsetAndRotation(0.3f, -8.0f, 0.6f, -0.24f, 0.0f, 0.22f));

        PartDefinition hairSweep3 = headHd.addOrReplaceChild("hair_sweep_3",
            CubeListBuilder.create().texOffs(88, 84)
                .addBox(-2.0f, -7.6f, -2.8f, 4.0f, 9.0f, 6.0f, new CubeDeformation(-0.25f)),
            PartPose.offsetAndRotation(3.4f, -18.4f, 2.4f, -0.54f, 0.0f, 0.78f));
        hairSweep3.addOrReplaceChild("hair_sweep_3t",
            CubeListBuilder.create().texOffs(112, 84)
                .addBox(-1.0f, -5.2f, -1.5f, 2.0f, 6.0f, 3.0f, new CubeDeformation(-0.15f)),
            PartPose.offsetAndRotation(0.2f, -7.2f, 0.5f, -0.24f, 0.0f, 0.20f));

        PartDefinition hairBackFin = headHd.addOrReplaceChild("hair_back_fin",
            CubeListBuilder.create().texOffs(88, 84)
                .addBox(-2.0f, -7.8f, -2.8f, 4.0f, 9.0f, 6.0f, new CubeDeformation(-0.30f)),
            PartPose.offsetAndRotation(-0.6f, -18.8f, 4.4f, -0.84f, 0.0f, 0.22f));
        hairBackFin.addOrReplaceChild("hair_back_fin_t",
            CubeListBuilder.create().texOffs(112, 84)
                .addBox(-1.0f, -5.0f, -1.5f, 2.0f, 6.0f, 3.0f, new CubeDeformation(-0.20f)),
            PartPose.offsetAndRotation(0.0f, -7.4f, 0.4f, -0.22f, 0.0f, 0.14f));

        // 两侧三段阶梯实体金属黄绿闪电耳翼（耳基座含黑曜凹槽 + 中段阶梯翼 + 锋锐天线尖）
        PartDefinition earWingR = headHd.addOrReplaceChild("ear_wing_r",
            CubeListBuilder.create().texOffs(156, 84).addBox(-1.8f, -5.8f, -2.5f, 3.0f, 7.0f, 5.0f),
            PartPose.offsetAndRotation(-9.6f, -9.8f, -2.8f, -0.18f, 0.08f, -0.32f));
        PartDefinition earMidR = earWingR.addOrReplaceChild("ear_mid_r",
            CubeListBuilder.create().texOffs(176, 84).addBox(-1.2f, -6.4f, -2.0f, 2.0f, 7.0f, 4.0f),
            PartPose.offsetAndRotation(-0.6f, -5.4f, 0.4f, -0.14f, 0.0f, -0.10f));
        earMidR.addOrReplaceChild("ear_tip_r",
            CubeListBuilder.create().texOffs(192, 84).addBox(-0.5f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f),
            PartPose.offsetAndRotation(-0.2f, -6.0f, 0.2f, -0.12f, 0.0f, -0.06f));

        PartDefinition earWingL = headHd.addOrReplaceChild("ear_wing_l",
            CubeListBuilder.create().texOffs(156, 84).addBox(-1.2f, -5.8f, -2.5f, 3.0f, 7.0f, 5.0f),
            PartPose.offsetAndRotation(9.6f, -9.8f, -2.8f, -0.18f, -0.08f, 0.32f));
        PartDefinition earMidL = earWingL.addOrReplaceChild("ear_mid_l",
            CubeListBuilder.create().texOffs(176, 84).addBox(-0.8f, -6.4f, -2.0f, 2.0f, 7.0f, 4.0f),
            PartPose.offsetAndRotation(0.6f, -5.4f, 0.4f, -0.14f, 0.0f, 0.10f));
        earMidL.addOrReplaceChild("ear_tip_l",
            CubeListBuilder.create().texOffs(192, 84).addBox(-0.5f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f),
            PartPose.offsetAndRotation(0.2f, -6.0f, 0.2f, -0.12f, 0.0f, 0.06f));

        // ==================== 2. BODY（胸甲、四点仪表、绿色电路、0.34x 超精细故障驱动器Ⅱ腰带、动态长风衣） ====================
        PartDefinition body = root.addOrReplaceChild("body",
            CubeListBuilder.create()
                .texOffs(0, 18).addBox(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, new CubeDeformation(0.30f))
                .texOffs(26, 18).addBox(-4.5f, 0.3f, -2.75f, 9.0f, 6.0f, 6.0f, new CubeDeformation(0.12f))
                .texOffs(92, 0).addBox(-3.8f, -1.3f, -2.4f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.20f))
                .texOffs(92, 0).addBox(1.8f, -1.3f, -2.4f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.20f))
                .texOffs(102, 0).addBox(-4.6f, 5.6f, -2.1f, 2.0f, 4.0f, 3.0f, new CubeDeformation(0.05f))
                .texOffs(102, 0).addBox(2.6f, 5.6f, -2.1f, 2.0f, 4.0f, 3.0f, new CubeDeformation(0.05f)),
            PartPose.ZERO);

        // 0.34x 亚体素精雕【故障驱动器Ⅱ（Buggle Driver II）】独立腰带组（支持人形态单独佩戴与变身态全息激活！）
        PartDefinition belt = body.addOrReplaceChild("belt_group",
            CubeListBuilder.create()
                // 高规格黑纹腰带环 + 后腰锁扣 + 两侧钛灰连接扣件
                .texOffs(0, 132).addBox(-12.5f, -2.5f, -7.2f, 25.0f, 5.0f, 2.0f, new CubeDeformation(0.08f))
                .texOffs(0, 132).addBox(-12.5f, -2.5f, 5.2f, 25.0f, 5.0f, 2.0f, new CubeDeformation(0.08f))
                .texOffs(60, 132).addBox(-13.1f, -2.5f, -6.5f, 2.0f, 5.0f, 13.0f, new CubeDeformation(0.05f))
                .texOffs(60, 132).addBox(11.1f, -2.5f, -6.5f, 2.0f, 5.0f, 13.0f, new CubeDeformation(0.05f))
                .texOffs(94, 132).addBox(-4.0f, -3.0f, 6.0f, 8.0f, 6.0f, 2.0f, new CubeDeformation(0.05f))
                .texOffs(116, 132).addBox(-11.4f, -3.0f, -8.4f, 3.0f, 6.0f, 2.0f)
                .texOffs(116, 132).addBox(8.4f, -3.0f, -8.4f, 3.0f, 6.0f, 2.0f)

                // 八角阶梯式钛银主框架 + 底部龙骨 + 顶部无缝拱桥
                .texOffs(0, 152).addBox(-9.5f, -3.0f, -10.3f, 18.0f, 6.0f, 3.0f)
                .texOffs(46, 152).addBox(-6.5f, -4.5f, -10.1f, 13.0f, 9.0f, 3.0f)
                .texOffs(80, 152).addBox(-5.0f, 3.5f, -10.3f, 10.0f, 2.0f, 3.0f, new CubeDeformation(-0.05f))
                .texOffs(142, 182).addBox(-4.5f, -6.4f, -10.1f, 9.0f, 3.0f, 3.0f)

                // 金属翡翠青切面外壳（双阶立体层次）
                .texOffs(0, 168).addBox(-8.5f, -2.5f, -11.2f, 17.0f, 5.0f, 2.0f)
                .texOffs(42, 168).addBox(-5.0f, -4.0f, -11.4f, 10.0f, 8.0f, 2.0f)

                // 中央八角透明青框 + 待机熏黑显示屏
                .texOffs(70, 168).addBox(-3.5f, -3.5f, -11.9f, 7.0f, 7.0f, 1.0f)
                .texOffs(88, 168).addBox(-3.0f, -3.0f, -12.25f, 6.0f, 6.0f, 1.0f)

                // 顶部银桥冠饰 + 正中发光青色指示灯 LED
                .texOffs(168, 168).addBox(-2.5f, -5.6f, -10.5f, 5.0f, 2.0f, 2.0f)
                .texOffs(184, 168).addBox(-1.5f, -5.3f, -11.4f, 3.0f, 2.0f, 1.0f, new CubeDeformation(-0.05f))

                // 左侧（-X）：圆形凹槽红「A」键 + 双管翡翠青光线枪喷嘴
                .texOffs(0, 182).addBox(-9.0f, -2.5f, -12.0f, 5.0f, 5.0f, 1.0f, new CubeDeformation(-0.10f))
                .texOffs(14, 182).addBox(-8.5f, -2.0f, -12.75f, 4.0f, 4.0f, 1.0f, new CubeDeformation(-0.15f))
                .texOffs(26, 182).addBox(-12.6f, -2.1f, -9.8f, 4.0f, 1.0f, 2.0f)
                .texOffs(26, 182).addBox(-12.6f, 0.4f, -9.8f, 4.0f, 1.0f, 2.0f)

                // 右侧（+X）：圆形铆钉青「B」键 + 翡翠青锯齿链锯外缘 + 枪灰银芯锯刃
                .texOffs(40, 182).addBox(4.0f, -2.5f, -12.0f, 5.0f, 5.0f, 1.0f, new CubeDeformation(-0.10f))
                .texOffs(54, 182).addBox(4.5f, -2.0f, -12.75f, 4.0f, 4.0f, 1.0f, new CubeDeformation(-0.15f))
                .texOffs(68, 182).addBox(8.0f, -2.0f, -9.6f, 7.0f, 4.0f, 2.0f)
                .texOffs(88, 182).addBox(7.8f, -1.5f, -10.05f, 6.0f, 3.0f, 2.0f),
            PartPose.offset(0.0f, 10.45f, 0.0f));

        // 顶部斜向卡槽桥 + 右肩导风斜坡 + 红色释放锁扣 + 左上青色三角宝石饰块
        belt.addOrReplaceChild("drv_dock_main",
            CubeListBuilder.create().texOffs(120, 168).addBox(-4.5f, -2.0f, -1.5f, 9.0f, 4.0f, 3.0f),
            PartPose.offsetAndRotation(-4.4f, -5.8f, -9.1f, 0.0f, 0.0f, -0.52f));
        belt.addOrReplaceChild("drv_dock_slope",
            CubeListBuilder.create().texOffs(146, 168).addBox(-3.5f, -1.5f, -1.4f, 7.0f, 3.0f, 3.0f),
            PartPose.offsetAndRotation(4.4f, -4.3f, -9.1f, 0.0f, 0.0f, 0.36f));
        belt.addOrReplaceChild("drv_red_latch",
            CubeListBuilder.create().texOffs(194, 168)
                .addBox(-1.0f, -1.4f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.10f)),
            PartPose.offsetAndRotation(3.2f, -5.9f, -9.8f, 0.0f, 0.0f, 0.24f));
        belt.addOrReplaceChild("drv_teal_tri",
            CubeListBuilder.create().texOffs(204, 168)
                .addBox(-1.0f, -1.0f, -0.9f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.15f)),
            PartPose.offsetAndRotation(-5.8f, -6.0f, -10.35f, 0.0f, 0.0f, -0.58f));

        // 变身激活组：中央全亮发光 Cronus 全息屏 + 左上斜插镂空握把《假面骑士编年史》荧光绿转玩卡带！
        PartDefinition gashatGroup = belt.addOrReplaceChild("driver_gashat_group",
            CubeListBuilder.create()
                .texOffs(104, 168).addBox(-3.0f, -3.0f, -12.50f, 6.0f, 6.0f, 1.0f),
            PartPose.ZERO);
        gashatGroup.addOrReplaceChild("gashat_angled",
            CubeListBuilder.create()
                .texOffs(108, 182).addBox(-1.8f, -7.8f, -1.0f, 4.0f, 8.0f, 2.0f)
                .texOffs(124, 182).addBox(-4.6f, -5.4f, -0.5f, 3.0f, 1.0f, 1.0f)
                .texOffs(134, 182).addBox(-4.8f, -4.8f, -0.5f, 1.0f, 6.0f, 1.0f)
                .texOffs(124, 182).addBox(-4.6f, 0.4f, -0.5f, 3.0f, 1.0f, 1.0f),
            PartPose.offsetAndRotation(-6.4f, -6.0f, -9.1f, 0.0f, 0.0f, -0.56f));

        // 标志性银边前开叉黑色绿纹长风衣（后摆 + 右摆 + 左摆，行走奔跑动态飘动）
        body.addOrReplaceChild("coat_back",
            CubeListBuilder.create().texOffs(0, 60)
                .addBox(-5.0f, 0.0f, -0.3f, 10.0f, 11.0f, 1.0f, new CubeDeformation(0.05f)),
            PartPose.offsetAndRotation(0.0f, 11.2f, 2.3f, 0.14f, 0.0f, 0.0f));
        body.addOrReplaceChild("coat_right",
            CubeListBuilder.create().texOffs(24, 60)
                .addBox(-2.6f, 0.0f, -2.6f, 4.0f, 11.0f, 5.0f, new CubeDeformation(0.12f)),
            PartPose.offsetAndRotation(-2.4f, 11.2f, 0.0f, 0.04f, 0.0f, 0.16f));
        body.addOrReplaceChild("coat_left",
            CubeListBuilder.create().texOffs(44, 60)
                .addBox(-1.4f, 0.0f, -2.6f, 4.0f, 11.0f, 5.0f, new CubeDeformation(0.12f)),
            PartPose.offsetAndRotation(2.4f, 11.2f, 0.0f, 0.04f, 0.0f, -0.16f));

        // ==================== 3. RIGHT & LEFT ARMS（全装甲手臂 + 标志性翼状肩甲与钩状竖刃） ====================
        PartDefinition rightArm = root.addOrReplaceChild("right_arm",
            CubeListBuilder.create().texOffs(0, 40)
                .addBox(-3.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.30f)),
            PartPose.offset(-5.0f, 2.0f, 0.0f));
        PartDefinition pauldronR = rightArm.addOrReplaceChild("pauldron_r",
            CubeListBuilder.create().texOffs(58, 18)
                .addBox(-3.4f, -1.4f, -3.0f, 6.0f, 4.0f, 6.0f, new CubeDeformation(0.05f)),
            PartPose.offsetAndRotation(-1.5f, -1.8f, 0.0f, 0.0f, 0.0f, 0.16f));
        pauldronR.addOrReplaceChild("horn_r",
            CubeListBuilder.create().texOffs(84, 18)
                .addBox(-0.7f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f, new CubeDeformation(0.05f)),
            PartPose.offsetAndRotation(-3.0f, -1.2f, 0.0f, 0.0f, 0.0f, -0.22f));
        pauldronR.addOrReplaceChild("blade_r",
            CubeListBuilder.create().texOffs(92, 18)
                .addBox(-0.6f, 0.0f, -1.0f, 1.0f, 3.0f, 2.0f),
            PartPose.offsetAndRotation(-3.0f, 2.2f, 0.0f, 0.0f, 0.0f, 0.20f));

        PartDefinition leftArm = root.addOrReplaceChild("left_arm",
            CubeListBuilder.create().texOffs(18, 40)
                .addBox(-1.0f, -2.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.30f)),
            PartPose.offset(5.0f, 2.0f, 0.0f));
        PartDefinition pauldronL = leftArm.addOrReplaceChild("pauldron_l",
            CubeListBuilder.create().texOffs(58, 18)
                .addBox(-2.6f, -1.4f, -3.0f, 6.0f, 4.0f, 6.0f, new CubeDeformation(0.05f)),
            PartPose.offsetAndRotation(1.5f, -1.8f, 0.0f, 0.0f, 0.0f, -0.16f));
        pauldronL.addOrReplaceChild("horn_l",
            CubeListBuilder.create().texOffs(84, 18)
                .addBox(-0.3f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f, new CubeDeformation(0.05f)),
            PartPose.offsetAndRotation(3.0f, -1.2f, 0.0f, 0.0f, 0.0f, 0.22f));
        pauldronL.addOrReplaceChild("blade_l",
            CubeListBuilder.create().texOffs(92, 18)
                .addBox(-0.4f, 0.0f, -1.0f, 1.0f, 3.0f, 2.0f),
            PartPose.offsetAndRotation(3.0f, 2.2f, 0.0f, 0.0f, 0.0f, -0.20f));

        // ==================== 4. RIGHT & LEFT LEGS（黑金底裤 + V型绿边护胫与战靴） ====================
        root.addOrReplaceChild("right_leg",
            CubeListBuilder.create().texOffs(36, 40)
                .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.28f)),
            PartPose.offset(-1.9f, 12.0f, 0.0f));
        root.addOrReplaceChild("left_leg",
            CubeListBuilder.create().texOffs(54, 40)
                .addBox(-2.0f, 0.0f, -2.0f, 4.0f, 12.0f, 4.0f, new CubeDeformation(0.28f)),
            PartPose.offset(1.9f, 12.0f, 0.0f));

        return LayerDefinition.create(mesh, 256, 256);
    }

    /** 渲染完整变身态假面骑士 Cronus 超精细 3D 装甲外观（含受光层 + 夜间全亮自发光层 + 动态风衣摆）。 */
    public void renderFullSuit(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               PlayerModel<AbstractClientPlayer> parent, AbstractClientPlayer player,
                               float limbSwing, float limbSwingAmount, float partialTick) {
        this.head.copyFrom(parent.head);
        this.body.copyFrom(parent.body);
        this.rightArm.copyFrom(parent.rightArm);
        this.leftArm.copyFrom(parent.leftArm);
        this.rightLeg.copyFrom(parent.rightLeg);
        this.leftLeg.copyFrom(parent.leftLeg);

        this.head.visible = true;
        this.headHd.visible = true;
        this.body.visible = true;
        this.beltGroup.visible = true;
        this.beltGashatGroup.visible = true; // 变身态腰带永远显示全息 Cronus 屏 + 斜插编年史卡带
        this.coatBack.visible = true;
        this.coatRight.visible = true;
        this.coatLeft.visible = true;
        this.rightArm.visible = true;
        this.rightPauldron.visible = true;
        this.leftArm.visible = true;
        this.leftPauldron.visible = true;
        this.rightLeg.visible = true;
        this.leftLeg.visible = true;

        // 动态长风衣摆随步幅、潜行与腾空自然飘扬，避免双腿穿模
        float swingR = parent.rightLeg.xRot;
        float swingL = parent.leftLeg.xRot;
        float speedFlare = Mth.clamp(limbSwingAmount, 0.0f, 1.0f) * 0.24f;
        float airLift = player.onGround() ? 0.0f : 0.18f;
        float sneakTilt = player.isCrouching() ? 0.16f : 0.0f;

        this.coatRight.xRot = 0.04f + swingR * 0.62f + sneakTilt * 0.4f;
        this.coatRight.zRot = 0.16f + speedFlare * 0.35f + airLift * 0.4f;
        this.coatLeft.xRot = 0.04f + swingL * 0.62f + sneakTilt * 0.4f;
        this.coatLeft.zRot = -0.16f - speedFlare * 0.35f - airLift * 0.4f;
        this.coatBack.xRot = 0.14f + speedFlare + airLift + sneakTilt * 0.6f
            + Math.max(0.0f, Math.max(swingR, swingL) * 0.35f);

        // Pass 1：受世界光照的本体超高清装甲（双面无剔除，风衣/耳翼/头冠/卡带镂空握把零背面穿帮）
        VertexConsumer baseVc = buffers.getBuffer(RenderType.entityCutoutNoCull(SUIT_TEX));
        this.root.render(pose, baseVc, light, overlay);

        // Pass 2：全亮自发光层（螺旋眼、胸口四点仪表与电路纹、驱动器全息 Cronus 屏/红A/青B/青LED/链锯光边）
        VertexConsumer emisVc = buffers.getBuffer(RenderType.eyes(SUIT_EMISSIVE_TEX));
        this.root.render(pose, emisVc, FULL_BRIGHT, overlay, FastColor.ARGB32.colorFromFloat(0.95f, 0.92f, 0.96f, 0.92f));
    }

    /** 人形态单独佩戴【故障驱动器Ⅱ】腰带时的超精细 3D 腰部渲染（随躯干俯仰/潜行精确贴合）。 */
    public void renderBeltOnly(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               PlayerModel<AbstractClientPlayer> parent, boolean showGashat) {
        pose.pushPose();
        parent.body.translateAndRotate(pose);
        this.beltGroup.visible = true;
        this.beltGashatGroup.visible = showGashat;

        VertexConsumer baseVc = buffers.getBuffer(RenderType.entityCutoutNoCull(SUIT_TEX));
        this.beltGroup.render(pose, baseVc, light, overlay);

        VertexConsumer emisVc = buffers.getBuffer(RenderType.eyes(SUIT_EMISSIVE_TEX));
        this.beltGroup.render(pose, emisVc, FULL_BRIGHT, overlay, FastColor.ARGB32.colorFromFloat(0.95f, 0.95f, 0.95f, 0.95f));
        pose.popPose();
    }

    /** 第一人称空手手臂渲染：仅渲染黑绿护臂拳套（隐藏肩甲避免遮挡第一人称视野）。 */
    public void renderFirstPersonArm(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                                     HumanoidArm arm) {
        ModelPart part = (arm == HumanoidArm.RIGHT) ? this.rightArm : this.leftArm;
        ModelPart pauldron = (arm == HumanoidArm.RIGHT) ? this.rightPauldron : this.leftPauldron;

        float sx = part.x, sy = part.y, sz = part.z;
        float rx = part.xRot, ry = part.yRot, rz = part.zRot;
        boolean prevPauldron = pauldron.visible;
        try {
            part.x = (arm == HumanoidArm.RIGHT) ? -5.0f : 5.0f;
            part.y = 2.0f;
            part.z = 0.0f;
            part.xRot = 0.0f;
            part.yRot = 0.0f;
            part.zRot = 0.0f;
            pauldron.visible = false;

            VertexConsumer baseVc = buffers.getBuffer(RenderType.entityCutoutNoCull(SUIT_TEX));
            part.render(pose, baseVc, light, overlay);

            VertexConsumer emisVc = buffers.getBuffer(RenderType.eyes(SUIT_EMISSIVE_TEX));
            part.render(pose, emisVc, FULL_BRIGHT, overlay, FastColor.ARGB32.colorFromFloat(0.92f, 0.90f, 0.95f, 0.90f));
        } finally {
            part.x = sx;
            part.y = sy;
            part.z = sz;
            part.xRot = rx;
            part.yRot = ry;
            part.zRot = rz;
            pauldron.visible = prevPauldron;
        }
    }
}
