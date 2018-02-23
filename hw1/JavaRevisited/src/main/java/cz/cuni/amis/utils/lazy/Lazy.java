package cz.cuni.amis.utils.lazy;

/**
 * Utility class for lazy initialization of objects.
 * <p><p>
 * {@link #create(Object)} is called in THREAD-SAFE manner, we guarantee to call it only once.
 */
public abstract class Lazy<T> {

    private T instance;
    private boolean createCalled = false;
    private final Object lock = new Object();

   /**
     * Creates lazy initialized object.
     * @return
     */
    abstract protected T create();

    /**
     * Once call will construct new object via {@link #create()}, successive calls
     * will return it. That is {@link #create()} may be called only once during
     * the life-time of the object ... ensure THREAD-SAFETY!
     * @return
     */
    public T get() {
        synchronized (this.lock) {
            if (!createCalled) {
                this.instance = this.create();
                this.createCalled = true;
            }
        }
        return instance;
    }
    
}
