package com.example.gulp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * Gives Minecraft Comes Alive villagers a belly. While a villager holds players in their tummy (VillagerHolding.syncBelly sends how full it is),
 * this draws a stretched copy of their torso bulging forward, the same way BellyLayer does for players. It uses MCA's own skin and
 * clothing textures (in MCA's order), so the outfit grows with the belly; with no MCA layers it uses the renderer's own texture.
 * Added to every MCA entity renderer that has a humanoid model (see ClientModEvents.addLayers).
 */
public class VillagerBellyLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final float MAX_EXTRA_DEPTH = 1.6f;
    private static final float MAX_EXTRA_WIDTH = 0.18f;

    private final ModelPart bellyFull, bellyLow, bellyFullClothing, bellyLowClothing;
    private final RenderLayerParent<T, M> parent;
    private List<Object> mcaLayers;

    public VillagerBellyLayer(RenderLayerParent<T, M> parent, EntityModelSet models) {
        super(parent);
        ModelPart root = models.bakeLayer(ClientModEvents.BELLY_LAYER);
        this.bellyFull = root.getChild("belly_full");
        this.bellyLow = root.getChild("belly_low");
        this.bellyFullClothing = root.getChild("belly_full_clothing");
        this.bellyLowClothing = root.getChild("belly_low_clothing");
        this.parent = parent;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T villager, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (villager.isInvisible()) return;
        float shown = ClientState.smoothedBelly(villager.getUUID());
        if (shown < 0.02f) return;
        if (GulpConfig.bellyStyle == GulpConfig.BellyStyle.OFF) return;
        if (!(getParentModel() instanceof HumanoidModel<?> humanoid)) return;

        boolean classic = GulpConfig.bellyStyle == GulpConfig.BellyStyle.CLASSIC;
        ModelPart belly = classic ? bellyFull : bellyLow;
        ModelPart clothing = classic ? bellyFullClothing : bellyLowClothing;

        float bulge = (float) Math.sqrt(shown);
        float depthScale = 1f + bulge * MAX_EXTRA_DEPTH;
        float widthScale = 1f + bulge * MAX_EXTRA_WIDTH;

        poseStack.pushPose();
        humanoid.body.translateAndRotate(poseStack); // follows the torso
        poseStack.translate(0f, 0f, 2f / 16f);
        poseStack.scale(widthScale, 1f, depthScale); // push out the front, from the back of the torso
        poseStack.translate(0f, 0f, -2f / 16f);

        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0f);
        if (mcaLayers == null) mcaLayers = McaSkins.findLayers(parent);
        List<McaSkins.Pass> passes = McaSkins.villagerPasses(villager, mcaLayers, partialTick);
        if (passes != null && !passes.isEmpty()) {
            for (McaSkins.Pass pass : passes) {
                VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(pass.texture()));
                (pass.clothing() ? clothing : belly).render(poseStack, vc, packedLight, overlay, pass.r(), pass.g(), pass.b(), 1f);
            }
        } else {
            ResourceLocation texture = parent.getTextureLocation(villager);
            VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(texture));
            belly.render(poseStack, vc, packedLight, overlay);
        }
        poseStack.popPose();
    }
}
