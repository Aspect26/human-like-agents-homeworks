package cz.cuni.amis.utils;

import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * This class allows you to combine several iterators in single one allowing you to seamlessly iterate over several
 * collections at once.
 * <p><p>
 * This class behaves as defined by {@link Iterator} contract.
 * 
 * @author Jimmy
 *
 * @param <NODE>
 */
public class Iterators<NODE> implements Iterator<NODE> {

	private final Iterator<NODE>[] iterators;
	private int currentIteratorIndex;
	
	/**
	 * Initialize this class to use "iterators" in the order as they are passed into the constructor.
	 * @param iterators may contain nulls
	 */
	@SuppressWarnings("unchecked")
	public Iterators(Iterator<NODE>... iterators) {
		int nonNullIteratorsCount = 0;
		for (Iterator<NODE> iterator : iterators) {
			if (iterator != null) {
				nonNullIteratorsCount++;
			}
		}
		this.iterators = new Iterator[nonNullIteratorsCount];
		int currentIndex = 0;
		for (Iterator<NODE> iterator : iterators) {
			if (iterator != null) {
				this.iterators[currentIndex] = iterator;
				currentIndex++;
			}
		}

		this.currentIteratorIndex = 0;
	}
	
	@Override
	public boolean hasNext() {
		if (this.iterators.length == 0) {
			return false;
		}
		if (this.iterators[this.currentIteratorIndex].hasNext()) {
			return true;
		}

		for (int index = this.currentIteratorIndex + 1; index < this.iterators.length; ++index) {
		    if (this.iterators[index].hasNext()) {
		        return true;
            }
        }

        return false;
	}

	@Override
	public NODE next() {
		if (this.iterators.length == 0) {
			throw new NoSuchElementException();
		}
		if (this.iterators[this.currentIteratorIndex].hasNext()) {
			return this.iterators[this.currentIteratorIndex].next();
		}
		if (this.currentIteratorIndex == this.iterators.length - 1) {
			throw new NoSuchElementException();
		}

		this.currentIteratorIndex++;
		return this.next();
	}

	@Override
	public void remove() {
        if (this.iterators.length == 0) {
            throw new IllegalStateException();
        }

        this.iterators[this.currentIteratorIndex].remove();
	}

}
