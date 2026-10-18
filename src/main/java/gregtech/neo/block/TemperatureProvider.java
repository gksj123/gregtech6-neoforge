package gregtech.neo.block;

public interface TemperatureProvider {
    long temperatureKelvin();

    long maximumTemperatureKelvin();

    String temperatureSourceName();
}
