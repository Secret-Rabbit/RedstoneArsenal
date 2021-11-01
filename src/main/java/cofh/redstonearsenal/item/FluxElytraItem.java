package cofh.redstonearsenal.item;

import cofh.core.init.CoreConfig;
import cofh.core.util.ProxyUtils;
import cofh.lib.util.helpers.SecurityHelper;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.attributes.Attribute;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.Attributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ElytraItem;
import net.minecraft.item.IArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static cofh.lib.util.helpers.StringHelper.*;
import static net.minecraft.util.text.TextFormatting.*;

public class FluxElytraItem extends ArmorItem implements IFluxItem {

    public static final double PROPEL_SPEED = 0.85;
    public static final double BRAKE_RATE = 0.90;
    public static final int BOOST_TIME = 40;
    public static final UUID CHEST_UUID = UUID.fromString("9F3D476D-C118-4544-8365-64846904B48E");

    protected int maxEnergy;
    protected int extract;
    protected int receive;
    protected int propelTime = 0;

    public FluxElytraItem(IArmorMaterial material, EquipmentSlotType slot, Properties builder, int maxEnergy, int maxTransfer) {

        super(material, slot, builder);

        this.maxEnergy = maxEnergy;
        this.extract = maxTransfer;
        this.receive = maxTransfer;

        ProxyUtils.registerItemModelProperty(this, new ResourceLocation("charged"), (stack, world, entity) -> getEnergyStored(stack) > 0 ? 1F : 0F);
        ProxyUtils.registerItemModelProperty(this, new ResourceLocation("active"), (stack, world, entity) -> getEnergyStored(stack) > 0 && isEmpowered(stack) ? 1F : 0F);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {

        tooltipDelegate(stack, worldIn, tooltip, flagIn);
    }

    @Override
    @Nullable
    public EquipmentSlotType getEquipmentSlot(ItemStack stack) {

        return EquipmentSlotType.CHEST;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlotType slot, ItemStack stack) {

        Multimap<Attribute, AttributeModifier> multimap = HashMultimap.create();
        if (slot == EquipmentSlotType.CHEST && hasEnergy(stack, false)) {
            multimap.put(Attributes.ARMOR, new AttributeModifier(CHEST_UUID, "Armor modifier", getDefense(), AttributeModifier.Operation.ADDITION));
            multimap.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(CHEST_UUID, "Armor toughness", getToughness(), AttributeModifier.Operation.ADDITION));
        }
        return multimap;
    }

    @Override
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {

        return hasEnergy(stack, false);
    }

    @Override
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {

        boolean shouldExtract = (flightTicks & 31) == 0 && !(entity instanceof PlayerEntity && ((PlayerEntity) entity).abilities.instabuild);
        useEnergy(stack, false, !shouldExtract);

        if (isEmpowered(stack)) {
            if (useEnergy(stack, true, !shouldExtract)) {
                if (propelTime < 0) {
                    brake(entity);
                }
                else if (propelTime == 0) {
                    propel(entity);
                }
            }
            else {
                propelTime = 1; //Stop propelling
            }
        }
        else if (propelTime > 0) {
            propel(entity);
            --propelTime;
        }

        return true;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {

        return hasEnergy(stack, false);
    }

    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, T entity, Consumer<T> onBroken) {

        amount = Math.min(getEnergyStored(stack), amount * getEnergyPerUse(false));
        useEnergy(stack, amount, entity instanceof PlayerEntity && ((PlayerEntity) entity).abilities.instabuild);
        return -1;
    }

    //Used to boost a single time, similar to a rocket.
    public boolean boost(ItemStack stack, LivingEntity entity, int boostTime) {

        if (!entity.getItemBySlot(EquipmentSlotType.CHEST).canElytraFly(entity)) {
            return false;
        }
        boolean isPlayer = entity instanceof PlayerEntity;
        boolean isCreative = isPlayer && ((PlayerEntity) entity).abilities.instabuild;
        if (!useEnergy(stack, true, isCreative)) {
            return false;
        }
        if (!entity.isFallFlying() && isPlayer) {
            ((PlayerEntity) entity).startFallFlying();
        }
        propel(entity, PROPEL_SPEED);
        propelTime = boostTime;

        return true;
    }

    public boolean boost(ItemStack stack, LivingEntity entity) {

        return boost(stack, entity, BOOST_TIME);
    }

    public void propel(LivingEntity entity, double speed) {

        if (entity.isFallFlying()) {
            Vector3d look = entity.getLookAngle();
            Vector3d velocity = entity.getDeltaMovement();
            entity.setDeltaMovement(velocity.add(look.x * speed - velocity.x * 0.5, look.y * speed - velocity.y * 0.5, look.z * speed - velocity.z * 0.5));
        }
    }

    public void propel(LivingEntity entity) {

        propel(entity, PROPEL_SPEED);
    }

    public void brake(LivingEntity entity, double rate) {

        if (entity.isFallFlying()) {
            Vector3d velocity = entity.getDeltaMovement();
            double horzBrake = velocity.x() * velocity.x() + velocity.z() * velocity.z() > 0.25 ? rate : 1;
            double vertBrake = velocity.y() * velocity.y() > 0.2 ? rate : 1;
            entity.setDeltaMovement(velocity.multiply(horzBrake, vertBrake, horzBrake));
        }
    }

    public void brake(LivingEntity entity) {

        brake(entity, BRAKE_RATE);
    }

    public int getPropelTime() {

        return propelTime;
    }

    public void setPropelTime(int time) {

        propelTime = time;
    }

    // region IEnergyContainerItem
    @Override
    public int getExtract(ItemStack container) {

        return extract;
    }

    @Override
    public int getReceive(ItemStack container) {

        return receive;
    }

    @Override
    public int getMaxEnergyStored(ItemStack container) {

        return getMaxStored(container, maxEnergy);
    }
    // endregion

    @Override
    public void onModeChange(PlayerEntity player, ItemStack stack) {

        IFluxItem.super.onModeChange(player, stack);

        if (stack.getItem() instanceof FluxElytraItem) {
            ((FluxElytraItem) stack.getItem()).setPropelTime(0);
        }
    }
}
