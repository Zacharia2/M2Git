package ts.realms.m2git.core.network.mws.fs;

import com.google.common.cache.CacheBuilder;

import java.util.Map;

import io.milton.cache.CacheManager;

public class LocalCacheManager implements CacheManager {

    private int maximumWeightedCapacity = 1000;

    public LocalCacheManager() {
    }


    @Override
    public Map getMap(String name) {
        return CacheBuilder.newBuilder()
            .maximumSize(maximumWeightedCapacity)
            .build().asMap();
    }

    public int getMaximumWeightedCapacity() {
        return maximumWeightedCapacity;
    }

    public void setMaximumWeightedCapacity(int maximumWeightedCapacity) {
        this.maximumWeightedCapacity = maximumWeightedCapacity;
    }
}
