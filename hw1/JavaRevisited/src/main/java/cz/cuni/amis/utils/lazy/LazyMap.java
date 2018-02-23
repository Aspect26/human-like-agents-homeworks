package cz.cuni.amis.utils.lazy;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps whose items are initialized on demand by {@link #create(Object)} method upon calling {@link #get(Object)} method 
 * over the key that the map does not have a value for yet.
 * <p><p>
 * {@link #create(Object)} is called in THREAD-SAFE manner, we guarantee to call it only once per non-existing key.
 * <p><p>
 * Example use:
 * <p><p>
 * Map<String, String> lazy = new LazyMap<String, String>() {
 *   protected abstract V create(String key) {
 *     return "EMPTY";
 *   }
 * }
 * String a = lazy.get("ahoj"); // will create key under "ahoj" and fill it with "EMPTY"
 * String b = lazy.get("ahoj"); // won't call create("ahoj") again as it already have a value for it
 * if (lazy.containsKey("cau")) {
 *   // won't get here as "cau" is not within map and it won't create a new entry within a map for it as it is only "containsKey" method
 * }
 * if (lazy.containsValue("nazdar") {
 *   // won't get here for obvious reasons
 * }
 * lazy.remove("ahoj");
 * lazy.get("ahoj"); // will call create("ahoj") again!
 */
public abstract class LazyMap<K, V> implements Map<K, V> {

    private final Map<K, V> data;
    private final Object lock = new Object();

    /**
     * Creates value for given key. THREAD-SAFE!
     * @param key
     * @return
     */
    protected abstract V create(K key);

    public LazyMap() {
        this.data = new HashMap<K, V>();
    }

    @Override
    public int size() {
    	return this.data.size();
    }

    @Override
    public boolean isEmpty() {
        return this.data.isEmpty();
    }

    @Override
    public boolean containsKey(Object key) {
        return this.data.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value) {
        return this.data.containsValue(value);
    }

    /**
     * This method should contain "thread-safe lazy initialization" if the 'key' is not present within the map.
     */
    @Override
    public V get(Object key) {
        // TODO: resolve this cast warnings
        synchronized (lock) {
            if (!this.data.containsKey(key)) {
                this.data.put((K)key, this.create((K)key));
            }
        }

    	return this.data.get(key);
    }

    @Override
    public V put(K key, V value) {
    	return this.data.put(key, value);
    }

    @Override
    public V remove(Object key) {
        return this.data.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m) {
        this.data.putAll(m);
    }

    @Override
    public void clear() {
        this.data.clear();
    }

    /**
     * Should not create any new values, just return those that are already within the map.
     */
    @Override
    public Set<K> keySet() {
        return this.data.keySet();
    }

    /**
     * Should not create any new values, just return those that are already within the map.
     */
    @Override
    public Collection<V> values() {
        return this.data.values();
    }

    /**
     * Should not create any new values, just return those that are already within the map.
     */
    @Override
    public Set<Entry<K, V>> entrySet() {
        return this.data.entrySet();
    }

}
