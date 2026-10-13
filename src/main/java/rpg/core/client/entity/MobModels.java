package rpg.core.client.entity;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** 자동 생성 (tools/mobgen.py) — 새 몹 모양. 직접 고치지 말고 생성기를 고치세요 */
public final class MobModels {
    private MobModels() {}

    public static LayerDefinition rat() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, -2.0F, -4.0F, 4.0F, 4.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 20.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(24, 0).addBox(-1.5F, -1.5F, -4.0F, 3.0F, 3.0F, 4.0F).texOffs(38, 0).addBox(-0.5F, -0.5F, -5.0F, 1.0F, 1.0F, 1.0F).texOffs(42, 0).addBox(-2.5F, -3.0F, -2.0F, 1.0F, 2.0F, 1.0F).texOffs(46, 0).addBox(1.5F, -3.0F, -2.0F, 1.0F, 2.0F, 1.0F),
                PartPose.offsetAndRotation(0.0F, 20.0F, -4.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg0", CubeListBuilder.create().texOffs(50, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(-1.5F, 22.5F, -2.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(54, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(1.5F, 22.5F, -2.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(58, 0).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(-1.5F, 22.5F, 2.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg3", CubeListBuilder.create().texOffs(0, 12).addBox(-0.5F, 0.0F, -0.5F, 1.0F, 1.5F, 1.0F),
                PartPose.offsetAndRotation(1.5F, 22.5F, 2.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(4, 12).addBox(-0.5F, -0.5F, 0.0F, 1.0F, 1.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 20.0F, 4.0F, -0.2617993877991494F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    public static LayerDefinition mushKid() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -3.0F, -4.0F, 8.0F, 3.0F, 8.0F).texOffs(32, 0).addBox(-3.0F, -4.0F, -3.0F, 6.0F, 1.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 11).addBox(-2.5F, 0.0F, -2.0F, 5.0F, 5.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 17.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(18, 11).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(1.3F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(26, 11).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offsetAndRotation(-1.3F, 22.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    public static LayerDefinition mushSpirit() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -6.0F, -3.0F, 6.0F, 6.0F, 6.0F).texOffs(0, 12).addBox(-6.0F, -10.0F, -6.0F, 12.0F, 4.0F, 12.0F).texOffs(0, 28).addBox(-4.0F, -11.0F, -4.0F, 8.0F, 1.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 28).addBox(-3.0F, 0.0F, -1.5F, 6.0F, 10.0F, 3.0F),
                PartPose.offsetAndRotation(0.0F, 8.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(50, 28).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                PartPose.offsetAndRotation(-4.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(0, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 9.0F, 2.0F),
                PartPose.offsetAndRotation(4.0F, 9.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(8, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(-1.5F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(16, 41).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 6.0F, 2.0F),
                PartPose.offsetAndRotation(1.5F, 18.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition mushSoldier() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F).texOffs(0, 16).addBox(-6.0F, -11.0F, -6.0F, 12.0F, 4.0F, 12.0F).texOffs(0, 32).addBox(-4.0F, -12.0F, -4.0F, 8.0F, 1.0F, 8.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(32, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_r", CubeListBuilder.create().texOffs(0, 48).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offsetAndRotation(-5.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("arm_l", CubeListBuilder.create().texOffs(16, 48).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offsetAndRotation(5.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_r", CubeListBuilder.create().texOffs(32, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offsetAndRotation(-1.9F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg_l", CubeListBuilder.create().texOffs(48, 48).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 12.0F, 4.0F),
                PartPose.offsetAndRotation(1.9F, 12.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition mushFairy() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 2.0F, 6.0F),
                PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(24, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 4.0F, 4.0F),
                PartPose.offsetAndRotation(0.0F, 16.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("wing_l", CubeListBuilder.create().texOffs(40, 0).addBox(0.0F, -4.0F, 0.0F, 6.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(1.5F, 17.0F, 1.5F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("wing_r", CubeListBuilder.create().texOffs(52, 0).addBox(-6.0F, -4.0F, 0.0F, 6.0F, 6.0F, 0.0F),
                PartPose.offsetAndRotation(-1.5F, 17.0F, 1.5F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 32);
    }

    public static LayerDefinition mushBeast() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-7.0F, -6.0F, -11.0F, 14.0F, 12.0F, 22.0F).texOffs(0, 34).addBox(-9.0F, -11.0F, -8.0F, 18.0F, 5.0F, 16.0F).texOffs(68, 34).addBox(-6.0F, -13.0F, -5.0F, 12.0F, 2.0F, 10.0F),
                PartPose.offsetAndRotation(0.0F, 10.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 55).addBox(-4.0F, -4.0F, -8.0F, 8.0F, 8.0F, 8.0F).texOffs(32, 55).addBox(-6.0F, -6.0F, -6.0F, 2.0F, 4.0F, 2.0F).texOffs(40, 55).addBox(4.0F, -6.0F, -6.0F, 2.0F, 4.0F, 2.0F),
                PartPose.offsetAndRotation(0.0F, 9.0F, -11.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg0", CubeListBuilder.create().texOffs(48, 55).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offsetAndRotation(-5.0F, 16.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg1", CubeListBuilder.create().texOffs(68, 55).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offsetAndRotation(5.0F, 16.0F, -8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg2", CubeListBuilder.create().texOffs(88, 55).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offsetAndRotation(-5.0F, 16.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("leg3", CubeListBuilder.create().texOffs(108, 55).addBox(-2.5F, 0.0F, -2.5F, 5.0F, 8.0F, 5.0F),
                PartPose.offsetAndRotation(5.0F, 16.0F, 8.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

}
