package cz.cuni.amis.utils.listener;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;

/**
 * This object is implementing listeners list, where you may store both type of references to 
 * the listeners (strong reference / weak reference).
 * <BR><BR>
 * It takes quite of effort to maintain the list with both strong / weak references,
 * therefore the class was created.
 * <BR><BR>
 * Because we don't know what method we will be calling on the listeners the public
 * interface ListenerNotifier exists. If you want to fire all the listeners, just
 * implement this interface and stuff it to the notify() method of the "Listeners".
 * (This somehow resembles the Stored Procedure pattern...)
 * <BR><BR>
 * The class is fully THREAD-SAFE.
 * 
 * @author Jimmy
 */
public class Listeners<Listener extends EventListener> {	
	
	/**
	 * Used to raise the event in the listeners.
	 * 
	 * @author Jimmy
	 *
	 * @param <Listener>
	 */
	public static interface ListenerNotifier<Listener extends EventListener> {
		
		public Object getEvent();
		
		public void notify(Listener listener);
		
	}

	private final List<Listener> stronglyReferencedListeners = new ArrayList<Listener>();
	private final List<WeakReference<Listener>> weaklyReferencedListeners = new ArrayList<WeakReference<Listener>>();

	// TODO: this may be broken into two locks -> one for operations with strongly referenced listeners and the second
    // one for the operations with weakly referenced listeners
	private final Object lock = new Object();
	
	/**
     * Adds listener with strong reference to it.
     * @param listener
     */
    public void addStrongListener(Listener listener) {
        synchronized (this.lock) {
    	    this.stronglyReferencedListeners.add(listener);
        }
    }
    
    /**
     * Adds listener with weak reference to it.
     * @param listener
     */
    public void addWeakListener(Listener listener) {
        synchronized (this.lock) {
            this.weaklyReferencedListeners.add(new WeakReference<Listener>(listener));
        }
    }
    
    /**
     * Removes all listeners that are == to this one (not equal()! must be the same object).
     * @param listenerToRemove
     * @return how many listeners were removed
     */
    public int removeListener(EventListener listenerToRemove) {
        int removedListeners = 0;
    	synchronized (this.lock) {
    	    for (int i = 0; i < this.weaklyReferencedListeners.size(); ++i) {
    	        // TODO: DRY pattern exception!
    	        if (this.weaklyReferencedListeners.get(i).get() == null) {
                    this.weaklyReferencedListeners.remove(i);
                    --i;
                }
    	        if (this.weaklyReferencedListeners.get(i).get() == listenerToRemove) {
    	            removedListeners++;
                    this.weaklyReferencedListeners.remove(i);
                    --i;
                }
            }

            for (int i = 0; i < this.stronglyReferencedListeners.size(); ++i) {
    	        if (this.stronglyReferencedListeners.get(i)== listenerToRemove) {
    	            removedListeners++;
    	            this.stronglyReferencedListeners.remove(i);
    	            --i;
                }
            }
        }

    	return removedListeners;
    }
    
    /**
     * Calls notifier.notify() on each of the stored listeners, allowing you to execute stored
     * command.
     * 
     * @param notifier
     */
    public void notify(ListenerNotifier<Listener> notifier) {
        synchronized (this.lock) {
            for (Listener listener : this.stronglyReferencedListeners) {
                notifier.notify(listener);
            }
            for (WeakReference<Listener> listenerReference : this.weaklyReferencedListeners) {
                // TODO: remove nulls here also?
                // Strong reference is required to not cause NPE in notify() call
                Listener listener = listenerReference.get();
                if (listener != null) {
                    notifier.notify(listener);
                }
            }
        }
    }
    
    /**
     * Returns true if at least one == listener to the param 'listener' is found.
     * <BR><BR>
     * Not using equal() but pointer ==.
     * 	 
     * @param queriedListener
     * @return
     */
    public boolean isListening(EventListener queriedListener) {
        synchronized (this.lock) {
            for (Listener listener : this.stronglyReferencedListeners) {
                if (listener == queriedListener) {
                    return true;
                }
            }

            for (WeakReference<Listener> weaklyReferencedListener : this.weaklyReferencedListeners) {
                // TODO: remove nulls also here?
                Listener listener = weaklyReferencedListener.get();
                if (listener != null && listener == queriedListener) {
                    return true;
                }
            }
        }

        return false;
    }
    
    public void clearListeners() {
        synchronized (this.lock) {
            this.stronglyReferencedListeners.clear();
            this.weaklyReferencedListeners.clear();
        }
    }
    
    /**
     * Returns count of listeners in the list, note that this may not be exact as we store also
     * listeners with weak listeners, but the list will be purged in next opportunity (like raising
     * event, removing listener).
     * <p><p>
     * Beware that, unlike in most collections, this method is
     * <em>NOT</em> a constant-time operation. Because of the
     * asynchronous nature of used queue, determining the current
     * number of elements requires an O(n) traversal.
     * 
     * @return
     */
    public int count() {
        synchronized (lock) {
            int count = this.stronglyReferencedListeners.size();
            for (WeakReference<Listener> listener : this.weaklyReferencedListeners) {
                if (listener.get() != null) {
                    ++count;
                }
            }

            return count;
        }
    }
    
}
