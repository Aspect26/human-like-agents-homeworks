package cz.cuni.amis.utils;

/**
 * N-argument key - used to store multiple keys within one object to provide n-argument key for maps.
 * <p><p>
 * The keys are not commutative!
 */
public class NKey {

    private static final int NULL_MAGIC_HASH_CODE = 26270;

	private int hashCode;
	private final Object[] keys;

	public NKey(Object... keys) {
		this.hashCode = 1;
		for (Object key : keys) {
			this.hashCode *= (key != null)? key.hashCode() : NULL_MAGIC_HASH_CODE;
		}

		this.keys = keys;
	}
        
	@Override
	public int hashCode() {
		return this.hashCode;
	}

	@Override
	public boolean equals(Object obj) {
	    if (!(obj instanceof NKey)) {
	        return false;
        }
        NKey other = (NKey) obj;
	    if (other.keys.length != this.keys.length) {
	        return false;
        }

        if (other.hashCode() != this.hashCode()) {
	        return false;
        }

        for (int i = 0; i < this.keys.length; ++i) {
	        if (this.keys[i] != other.keys[i]) {
	            return false;
            }
        }

        return true;
	}

}
