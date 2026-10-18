package gregtech.neo.block;

import gregtech.neo.registry.GTBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TemperatureSensorBlockEntity extends BlockEntity implements TemperatureProvider {
    private static final long ENVIRONMENT_TEMPERATURE = 293L;
    private long reading = ENVIRONMENT_TEMPERATURE;
    private long maximum;
    private String sourceName = "Environment";

    public TemperatureSensorBlockEntity(BlockPos pos, BlockState state) {
        super(GTBlockEntities.TEMPERATURE_SENSOR.get(), pos, state);
    }

    public void tick() {
        if (level == null || level.isClientSide() || level.getGameTime() % 10 != 0) {
            return;
        }
        long newReading = ENVIRONMENT_TEMPERATURE;
        long newMaximum = 0;
        String newSource = "Environment";
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(direction)) instanceof TemperatureProvider provider) {
                newReading = provider.temperatureKelvin();
                newMaximum = provider.maximumTemperatureKelvin();
                newSource = provider.temperatureSourceName();
                break;
            }
        }
        if (newReading != reading || newMaximum != maximum || !newSource.equals(sourceName)) {
            reading = newReading;
            maximum = newMaximum;
            sourceName = newSource;
            setChanged();
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    public int analogSignal() {
        if (maximum <= ENVIRONMENT_TEMPERATURE) {
            return reading > ENVIRONMENT_TEMPERATURE ? 15 : 0;
        }
        long range = maximum - ENVIRONMENT_TEMPERATURE;
        long current = Math.max(0, reading - ENVIRONMENT_TEMPERATURE);
        return (int) Math.min(15, current * 15 / range);
    }

    @Override
    public long temperatureKelvin() {
        return reading;
    }

    @Override
    public long maximumTemperatureKelvin() {
        return maximum;
    }

    @Override
    public String temperatureSourceName() {
        return sourceName;
    }

    public Component describe() {
        return Component.translatable(
            "message.gregtech.temperature_sensor.status",
            sourceName,
            reading,
            maximum,
            analogSignal()
        );
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        reading = tag.contains("reading") ? tag.getLong("reading") : ENVIRONMENT_TEMPERATURE;
        maximum = tag.getLong("maximum");
        sourceName = tag.contains("source_name") ? tag.getString("source_name") : "Environment";
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("reading", reading);
        tag.putLong("maximum", maximum);
        tag.putString("source_name", sourceName);
    }
}
