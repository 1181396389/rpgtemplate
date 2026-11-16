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
        new ResourceLocation("rpgtemplate", "textures/entity/cronus_suit.png");
    private static final ResourceLocation SUIT_EMISSIVE_TEX =
        new ResourceLocation("rpgtemplate", "textures/entity/cronus_suit_emissive.png");
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
        this.root = createLayerDefinition().m_171564_();
        this.head = root.m_171324_("head");
        this.headHd = head.m_171324_("head_hd");
        this.headHd.f_233553_ = 0.40f;
        this.headHd.f_233554_ = 0.40f;
        this.headHd.f_233555_ = 0.40f;

        this.body = root.m_171324_("body");
        this.beltGroup = body.m_171324_("belt_group");
        this.beltGroup.f_233553_ = 0.34f;
        this.beltGroup.f_233554_ = 0.34f;
        this.beltGroup.f_233555_ = 0.34f;
        this.beltGashatGroup = beltGroup.m_171324_("driver_gashat_group");

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

        // ==================== 1. HEAD（0.40x 亚体素实体精雕 S.H.Figuarts 级 Cronus 头盔，零空心薄片感） ====================
        PartDefinition head = root.m_171599_("head", CubeListBuilder.m_171558_(), PartPose.f_171404_);
        PartDefinition headHd = head.m_171599_("head_hd",
            CubeListBuilder.m_171558_()
                // 上部圆弧盔顶 + 阶梯收窄顶冠 + 窄收下颌内芯（塑造真正收窄式骑士头盔剪影而非方块！）
                .m_171514_(0, 200).m_171488_(-9.0f, -18.8f, -9.0f, 18.0f, 12.0f, 18.0f, new CubeDeformation(0.25f))
                .m_171514_(74, 200).m_171488_(-7.5f, -21.2f, -8.0f, 15.0f, 3.0f, 16.0f, new CubeDeformation(0.20f))
                .m_171514_(138, 200).m_171488_(-7.0f, -6.8f, -7.8f, 14.0f, 7.0f, 15.0f, new CubeDeformation(0.20f))
                // 内嵌式熏黑 Ex-Aid 复眼护目镜（36x18 超清同心翠绿螺旋纹）+ 冷峻黑曜倒 V 呼吸面罩
                .m_171514_(0, 232).m_171481_(-9.0f, -15.4f, -10.0f, 18.0f, 9.0f, 2.0f)
                .m_171514_(44, 232).m_171481_(-7.0f, -7.0f, -10.4f, 14.0f, 6.0f, 3.0f),
            PartPose.f_171404_);

        // 立体金属黄绿 V 型眉骨护额（悬挑覆盖熏黑护目镜上沿）
        headHd.m_171599_("h_brow_r",
            CubeListBuilder.m_171558_().m_171514_(108, 232).m_171481_(-5.0f, -1.0f, -1.0f, 10.0f, 2.0f, 2.0f),
            PartPose.m_171423_(-5.4f, -15.2f, -10.6f, 0.12f, 0.16f, 0.34f));
        headHd.m_171599_("h_brow_l",
            CubeListBuilder.m_171558_().m_171514_(108, 232).m_171481_(-5.0f, -1.0f, -1.0f, 10.0f, 2.0f, 2.0f),
            PartPose.m_171423_(5.4f, -15.2f, -10.6f, 0.12f, -0.16f, -0.34f));

        // 立体实体斜切侧颊甲 + 金属黄绿尖锐 V 型下巴护盾
        headHd.m_171599_("h_cheek_r",
            CubeListBuilder.m_171558_().m_171514_(136, 232).m_171481_(-1.5f, -3.5f, -3.0f, 3.0f, 7.0f, 5.0f),
            PartPose.m_171423_(-8.4f, -5.2f, -6.2f, 0.10f, 0.24f, -0.22f));
        headHd.m_171599_("h_cheek_l",
            CubeListBuilder.m_171558_().m_171514_(136, 232).m_171481_(-1.5f, -3.5f, -3.0f, 3.0f, 7.0f, 5.0f),
            PartPose.m_171423_(8.4f, -5.2f, -6.2f, 0.10f, -0.24f, 0.22f));
        headHd.m_171599_("h_chin_shield",
            CubeListBuilder.m_171558_().m_171514_(82, 232).m_171481_(-4.0f, -1.8f, -1.5f, 8.0f, 4.0f, 3.0f),
            PartPose.m_171423_(0.0f, -1.4f, -10.2f, -0.26f, 0.0f, 0.0f));

        // 中央前额 V 刃（2 段实体棱锥）
        PartDefinition hairVCenter = headHd.m_171599_("hair_v_center",
            CubeListBuilder.m_171558_().m_171514_(0, 84).m_171481_(-1.5f, -6.5f, -1.5f, 3.0f, 7.0f, 3.0f),
            PartPose.m_171423_(0.0f, -15.0f, -10.8f, -0.16f, 0.0f, 0.0f));
        hairVCenter.m_171599_("hair_v_tip",
            CubeListBuilder.m_171558_().m_171514_(14, 84).m_171481_(-1.0f, -4.8f, -1.0f, 2.0f, 5.0f, 2.0f),
            PartPose.m_171423_(0.0f, -6.2f, -0.2f, -0.14f, 0.0f, 0.0f));

        // 两侧斜飞前额刘海发刺（2 段实体棱锥）
        PartDefinition hairBangLeft = headHd.m_171599_("hair_bang_left",
            CubeListBuilder.m_171558_().m_171514_(126, 84).m_171481_(-1.5f, -6.5f, -2.0f, 3.0f, 7.0f, 4.0f),
            PartPose.m_171423_(-3.6f, -18.0f, -8.8f, -0.18f, 0.14f, -0.48f));
        hairBangLeft.m_171599_("hair_bang_left_t",
            CubeListBuilder.m_171558_().m_171514_(144, 84).m_171481_(-1.0f, -4.8f, -1.0f, 2.0f, 5.0f, 2.0f),
            PartPose.m_171423_(0.0f, -6.2f, 0.2f, -0.18f, 0.0f, -0.14f));
        PartDefinition hairBangRight = headHd.m_171599_("hair_bang_right",
            CubeListBuilder.m_171558_().m_171514_(126, 84).m_171481_(-1.5f, -6.8f, -2.0f, 3.0f, 7.0f, 4.0f),
            PartPose.m_171423_(3.8f, -18.2f, -8.6f, -0.18f, -0.14f, 0.52f));
        hairBangRight.m_171599_("hair_bang_right_t",
            CubeListBuilder.m_171558_().m_171514_(144, 84).m_171481_(-1.0f, -4.8f, -1.0f, 2.0f, 5.0f, 2.0f),
            PartPose.m_171423_(0.0f, -6.5f, 0.2f, -0.18f, 0.0f, 0.16f));

        // 标志性不对称向右上弯曲后掠 Ex-Aid 镰刀主发刺冠（3 段实体弧形渐变棱锥，带金属绿前刃与皇室蓝黑背面）
        PartDefinition hairSweep1 = headHd.m_171599_("hair_sweep_1",
            CubeListBuilder.m_171558_().m_171514_(24, 84).m_171481_(-2.5f, -7.5f, -3.5f, 5.0f, 8.0f, 7.0f),
            PartPose.m_171423_(-2.0f, -20.0f, -5.6f, -0.24f, 0.0f, 0.34f));
        PartDefinition hairSweep1m = hairSweep1.m_171599_("hair_sweep_1m",
            CubeListBuilder.m_171558_().m_171514_(52, 84).m_171481_(-2.0f, -7.5f, -2.5f, 4.0f, 8.0f, 5.0f),
            PartPose.m_171423_(0.3f, -7.2f, 0.6f, -0.22f, 0.0f, 0.24f));
        hairSweep1m.m_171599_("hair_sweep_1t",
            CubeListBuilder.m_171558_().m_171514_(74, 84).m_171481_(-1.0f, -5.6f, -1.5f, 2.0f, 6.0f, 3.0f),
            PartPose.m_171423_(0.3f, -7.2f, 0.6f, -0.22f, 0.0f, 0.22f));

        // 次级层叠后掠发刺 + 侧翼后掠发刺 + 后脑气动脊刃（均为 2 段实体弯曲棱锥）
        PartDefinition hairSweep2 = headHd.m_171599_("hair_sweep_2",
            CubeListBuilder.m_171558_().m_171514_(88, 84).m_171481_(-2.0f, -8.4f, -3.0f, 4.0f, 9.0f, 6.0f),
            PartPose.m_171423_(1.2f, -19.6f, -1.4f, -0.38f, 0.0f, 0.56f));
        hairSweep2.m_171599_("hair_sweep_2t",
            CubeListBuilder.m_171558_().m_171514_(112, 84).m_171481_(-1.0f, -5.6f, -1.5f, 2.0f, 6.0f, 3.0f),
            PartPose.m_171423_(0.3f, -8.0f, 0.6f, -0.24f, 0.0f, 0.22f));

        PartDefinition hairSweep3 = headHd.m_171599_("hair_sweep_3",
            CubeListBuilder.m_171558_().m_171514_(88, 84)
                .m_171488_(-2.0f, -7.6f, -2.8f, 4.0f, 9.0f, 6.0f, new CubeDeformation(-0.25f)),
            PartPose.m_171423_(3.4f, -18.4f, 2.4f, -0.54f, 0.0f, 0.78f));
        hairSweep3.m_171599_("hair_sweep_3t",
            CubeListBuilder.m_171558_().m_171514_(112, 84)
                .m_171488_(-1.0f, -5.2f, -1.5f, 2.0f, 6.0f, 3.0f, new CubeDeformation(-0.15f)),
            PartPose.m_171423_(0.2f, -7.2f, 0.5f, -0.24f, 0.0f, 0.20f));

        PartDefinition hairBackFin = headHd.m_171599_("hair_back_fin",
            CubeListBuilder.m_171558_().m_171514_(88, 84)
                .m_171488_(-2.0f, -7.8f, -2.8f, 4.0f, 9.0f, 6.0f, new CubeDeformation(-0.30f)),
            PartPose.m_171423_(-0.6f, -18.8f, 4.4f, -0.84f, 0.0f, 0.22f));
        hairBackFin.m_171599_("hair_back_fin_t",
            CubeListBuilder.m_171558_().m_171514_(112, 84)
                .m_171488_(-1.0f, -5.0f, -1.5f, 2.0f, 6.0f, 3.0f, new CubeDeformation(-0.20f)),
            PartPose.m_171423_(0.0f, -7.4f, 0.4f, -0.22f, 0.0f, 0.14f));

        // 两侧三段阶梯实体金属黄绿闪电耳翼（耳基座含黑曜凹槽 + 中段阶梯翼 + 锋锐天线尖）
        PartDefinition earWingR = headHd.m_171599_("ear_wing_r",
            CubeListBuilder.m_171558_().m_171514_(156, 84).m_171481_(-1.8f, -5.8f, -2.5f, 3.0f, 7.0f, 5.0f),
            PartPose.m_171423_(-9.6f, -9.8f, -2.8f, -0.18f, 0.08f, -0.32f));
        PartDefinition earMidR = earWingR.m_171599_("ear_mid_r",
            CubeListBuilder.m_171558_().m_171514_(176, 84).m_171481_(-1.2f, -6.4f, -2.0f, 2.0f, 7.0f, 4.0f),
            PartPose.m_171423_(-0.6f, -5.4f, 0.4f, -0.14f, 0.0f, -0.10f));
        earMidR.m_171599_("ear_tip_r",
            CubeListBuilder.m_171558_().m_171514_(192, 84).m_171481_(-0.5f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f),
            PartPose.m_171423_(-0.2f, -6.0f, 0.2f, -0.12f, 0.0f, -0.06f));

        PartDefinition earWingL = headHd.m_171599_("ear_wing_l",
            CubeListBuilder.m_171558_().m_171514_(156, 84).m_171481_(-1.2f, -5.8f, -2.5f, 3.0f, 7.0f, 5.0f),
            PartPose.m_171423_(9.6f, -9.8f, -2.8f, -0.18f, -0.08f, 0.32f));
        PartDefinition earMidL = earWingL.m_171599_("ear_mid_l",
            CubeListBuilder.m_171558_().m_171514_(176, 84).m_171481_(-0.8f, -6.4f, -2.0f, 2.0f, 7.0f, 4.0f),
            PartPose.m_171423_(0.6f, -5.4f, 0.4f, -0.14f, 0.0f, 0.10f));
        earMidL.m_171599_("ear_tip_l",
            CubeListBuilder.m_171558_().m_171514_(192, 84).m_171481_(-0.5f, -4.8f, -1.0f, 1.0f, 5.0f, 2.0f),
            PartPose.m_171423_(0.2f, -6.0f, 0.2f, -0.12f, 0.0f, 0.06f));

        // ==================== 2. BODY（胸甲、四点仪表、绿色电路、0.34x 超精细故障驱动器Ⅱ腰带、动态长风衣） ====================
        PartDefinition body = root.m_171599_("body",
            CubeListBuilder.m_171558_()
                .m_171514_(0, 18).m_171488_(-4.0f, 0.0f, -2.0f, 8.0f, 12.0f, 4.0f, new CubeDeformation(0.30f))
                .m_171514_(26, 18).m_171488_(-4.5f, 0.3f, -2.75f, 9.0f, 6.0f, 6.0f, new CubeDeformation(0.12f))
                .m_171514_(92, 0).m_171488_(-3.8f, -1.3f, -2.4f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.20f))
                .m_171514_(92, 0).m_171488_(1.8f, -1.3f, -2.4f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.20f))
                .m_171514_(102, 0).m_171488_(-4.6f, 5.6f, -2.1f, 2.0f, 4.0f, 3.0f, new CubeDeformation(0.05f))
                .m_171514_(102, 0).m_171488_(2.6f, 5.6f, -2.1f, 2.0f, 4.0f, 3.0f, new CubeDeformation(0.05f)),
            PartPose.f_171404_);

        // 0.34x 亚体素精雕【故障驱动器Ⅱ（Buggle Driver II）】独立腰带组（支持人形态单独佩戴与变身态全息激活！）
        PartDefinition belt = body.m_171599_("belt_group",
            CubeListBuilder.m_171558_()
                // 高规格黑纹腰带环 + 后腰锁扣 + 两侧钛灰连接扣件
                .m_171514_(0, 132).m_171488_(-12.5f, -2.5f, -7.2f, 25.0f, 5.0f, 2.0f, new CubeDeformation(0.08f))
                .m_171514_(0, 132).m_171488_(-12.5f, -2.5f, 5.2f, 25.0f, 5.0f, 2.0f, new CubeDeformation(0.08f))
                .m_171514_(60, 132).m_171488_(-13.1f, -2.5f, -6.5f, 2.0f, 5.0f, 13.0f, new CubeDeformation(0.05f))
                .m_171514_(60, 132).m_171488_(11.1f, -2.5f, -6.5f, 2.0f, 5.0f, 13.0f, new CubeDeformation(0.05f))
                .m_171514_(94, 132).m_171488_(-4.0f, -3.0f, 6.0f, 8.0f, 6.0f, 2.0f, new CubeDeformation(0.05f))
                .m_171514_(116, 132).m_171481_(-11.4f, -3.0f, -8.4f, 3.0f, 6.0f, 2.0f)
                .m_171514_(116, 132).m_171481_(8.4f, -3.0f, -8.4f, 3.0f, 6.0f, 2.0f)

                // 八角阶梯式钛银主框架 + 底部龙骨 + 顶部无缝拱桥
                .m_171514_(0, 152).m_171481_(-9.5f, -3.0f, -10.3f, 18.0f, 6.0f, 3.0f)
                .m_171514_(46, 152).m_171481_(-6.5f, -4.5f, -10.1f, 13.0f, 9.0f, 3.0f)
                .m_171514_(80, 152).m_171488_(-5.0f, 3.5f, -10.3f, 10.0f, 2.0f, 3.0f, new CubeDeformation(-0.05f))
                .m_171514_(142, 182).m_171481_(-4.5f, -6.4f, -10.1f, 9.0f, 3.0f, 3.0f)

                // 金属翡翠青切面外壳（双阶立体层次）
                .m_171514_(0, 168).m_171481_(-8.5f, -2.5f, -11.2f, 17.0f, 5.0f, 2.0f)
                .m_171514_(42, 168).m_171481_(-5.0f, -4.0f, -11.4f, 10.0f, 8.0f, 2.0f)

                // 中央八角透明青框 + 待机熏黑显示屏
                .m_171514_(70, 168).m_171481_(-3.5f, -3.5f, -11.9f, 7.0f, 7.0f, 1.0f)
                .m_171514_(88, 168).m_171481_(-3.0f, -3.0f, -12.25f, 6.0f, 6.0f, 1.0f)

                // 顶部银桥冠饰 + 正中发光青色指示灯 LED
                .m_171514_(168, 168).m_171481_(-2.5f, -5.6f, -10.5f, 5.0f, 2.0f, 2.0f)
                .m_171514_(184, 168).m_171488_(-1.5f, -5.3f, -11.4f, 3.0f, 2.0f, 1.0f, new CubeDeformation(-0.05f))

                // 左侧（-X）：圆形凹槽红「A」键 + 双管翡翠青光线枪喷嘴
                .m_171514_(0, 182).m_171488_(-9.0f, -2.5f, -12.0f, 5.0f, 5.0f, 1.0f, new CubeDeformation(-0.10f))
                .m_171514_(14, 182).m_171488_(-8.5f, -2.0f, -12.75f, 4.0f, 4.0f, 1.0f, new CubeDeformation(-0.15f))
                .m_171514_(26, 182).m_171481_(-12.6f, -2.1f, -9.8f, 4.0f, 1.0f, 2.0f)
                .m_171514_(26, 182).m_171481_(-12.6f, 0.4f, -9.8f, 4.0f, 1.0f, 2.0f)

                // 右侧（+X）：圆形铆钉青「B」键 + 翡翠青锯齿链锯外缘 + 枪灰银芯锯刃
                .m_171514_(40, 182).m_171488_(4.0f, -2.5f, -12.0f, 5.0f, 5.0f, 1.0f, new CubeDeformation(-0.10f))
                .m_171514_(54, 182).m_171488_(4.5f, -2.0f, -12.75f, 4.0f, 4.0f, 1.0f, new CubeDeformation(-0.15f))
                .m_171514_(68, 182).m_171481_(8.0f, -2.0f, -9.6f, 7.0f, 4.0f, 2.0f)
                .m_171514_(88, 182).m_171481_(7.8f, -1.5f, -10.05f, 6.0f, 3.0f, 2.0f),
            PartPose.m_171419_(0.0f, 10.45f, 0.0f));

        // 顶部斜向卡槽桥 + 右肩导风斜坡 + 红色释放锁扣 + 左上青色三角宝石饰块
        belt.m_171599_("drv_dock_main",
            CubeListBuilder.m_171558_().m_171514_(120, 168).m_171481_(-4.5f, -2.0f, -1.5f, 9.0f, 4.0f, 3.0f),
            PartPose.m_171423_(-4.4f, -5.8f, -9.1f, 0.0f, 0.0f, -0.52f));
        belt.m_171599_("drv_dock_slope",
            CubeListBuilder.m_171558_().m_171514_(146, 168).m_171481_(-3.5f, -1.5f, -1.4f, 7.0f, 3.0f, 3.0f),
            PartPose.m_171423_(4.4f, -4.3f, -9.1f, 0.0f, 0.0f, 0.36f));
        belt.m_171599_("drv_red_latch",
            CubeListBuilder.m_171558_().m_171514_(194, 168)
                .m_171488_(-1.0f, -1.4f, -1.0f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.10f)),
            PartPose.m_171423_(3.2f, -5.9f, -9.8f, 0.0f, 0.0f, 0.24f));
        belt.m_171599_("drv_teal_tri",
            CubeListBuilder.m_171558_().m_171514_(204, 168)
                .m_171488_(-1.0f, -1.0f, -0.9f, 2.0f, 2.0f, 2.0f, new CubeDeformation(-0.15f)),
            PartPose.m_171423_(-5.8f, -6.0f, -10.35f, 0.0f, 0.0f, -0.58f));

        // 变身激活组：中央全亮发光 Cronus 全息屏 + 左上斜插镂空握把《假面骑士编年史》荧光绿转玩卡带！
        PartDefinition gashatGroup = belt.m_171599_("driver_gashat_group",
            CubeListBuilder.m_171558_()
                .m_171514_(104, 168).m_171481_(-3.0f, -3.0f, -12.50f, 6.0f, 6.0f, 1.0f),
            PartPose.f_171404_);
        gashatGroup.m_171599_("gashat_angled",
            CubeListBuilder.m_171558_()
                .m_171514_(108, 182).m_171481_(-1.8f, -7.8f, -1.0f, 4.0f, 8.0f, 2.0f)
                .m_171514_(124, 182).m_171481_(-4.6f, -5.4f, -0.5f, 3.0f, 1.0f, 1.0f)
                .m_171514_(134, 182).m_171481_(-4.8f, -4.8f, -0.5f, 1.0f, 6.0f, 1.0f)
                .m_171514_(124, 182).m_171481_(-4.6f, 0.4f, -0.5f, 3.0f, 1.0f, 1.0f),
            PartPose.m_171423_(-6.4f, -6.0f, -9.1f, 0.0f, 0.0f, -0.56f));

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

        return LayerDefinition.m_171565_(mesh, 256, 256);
    }

    /** 渲染完整变身态假面骑士 Cronus 超精细 3D 装甲外观（含受光层 + 夜间全亮自发光层 + 动态风衣摆）。 */
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
        this.headHd.f_104207_ = true;
        this.body.f_104207_ = true;
        this.beltGroup.f_104207_ = true;
        this.beltGashatGroup.f_104207_ = true; // 变身态腰带永远显示全息 Cronus 屏 + 斜插编年史卡带
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

        // Pass 1：受世界光照的本体超高清装甲（双面无剔除，风衣/耳翼/头冠/卡带镂空握把零背面穿帮）
        VertexConsumer baseVc = buffers.m_6299_(RenderType.m_110458_(SUIT_TEX));
        this.root.m_104301_(pose, baseVc, light, overlay);

        // Pass 2：全亮自发光层（螺旋眼、胸口四点仪表与电路纹、驱动器全息 Cronus 屏/红A/青B/青LED/链锯光边）
        VertexConsumer emisVc = buffers.m_6299_(RenderType.m_110488_(SUIT_EMISSIVE_TEX));
        this.root.m_104306_(pose, emisVc, FULL_BRIGHT, overlay, 0.92f, 0.96f, 0.92f, 0.95f);
    }

    /** 人形态单独佩戴【故障驱动器Ⅱ】腰带时的超精细 3D 腰部渲染（随躯干俯仰/潜行精确贴合）。 */
    public void renderBeltOnly(PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                               PlayerModel<AbstractClientPlayer> parent, boolean showGashat) {
        pose.m_85836_();
        parent.f_102810_.m_104299_(pose);
        this.beltGroup.f_104207_ = true;
        this.beltGashatGroup.f_104207_ = showGashat;

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
