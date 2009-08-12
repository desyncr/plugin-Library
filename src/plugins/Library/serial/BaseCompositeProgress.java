/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package plugins.Library.serial;

import plugins.Library.serial.Serialiser.*;

/**
** A progress that accumulates its data from the given group of progresses.
**
** TODO perhaps synchronize
**
** PRIORITY incorporate finalTotalEstimate into the status
**
** DOCUMENT
**
** @author infinity0
*/
public class BaseCompositeProgress implements CompositeProgress {

	protected String subject = "??";

	protected Iterable<? extends Progress> subprogress = java.util.Collections.emptyList();

	protected EstimateType esttype = EstimateType.NONE;

	public enum EstimateType { NONE, EQUAL_TO_KNOWN }

	public BaseCompositeProgress() { }

	/**
	** Sets the iterable to accumulate progress information from. There are
	** several static helper methods you can use to create progress iterables
	** that are backed by a collection of tracker ids; see the rest of the
	** documentation for this class.
	**
	** @param subs The subprogresses to accumulating information from
	*/
	public void setSubProgress(Iterable<? extends Progress> subs) {
		if (subs == null) {
			throw new IllegalArgumentException("Can't set a null progress iterable");
		}
		subprogress = subs;
	}

	public void setSubject(String s) {
		subject = s;
	}

	public void setEstimateType(EstimateType est) {
		esttype = est;
	}

	/*========================================================================
	  public interface Progress
	 ========================================================================*/

	@Override public String getSubject() {
		return subject;
	}

	@Override public String getStatus() {
		try {
			return getParts().toString();
		} catch (TaskAbortException e) {
			assert(e.isError()); // in-progress / complete tasks should have been rm'd from the underlying iterable
			return "Aborted: " + e.getMessage();
		}
	}

	@Override public ProgressParts getParts() throws TaskAbortException {
		switch (esttype) {
		case NONE:
			return ProgressParts.getParts(subprogress, -1);
		case EQUAL_TO_KNOWN:
			return ProgressParts.getParts(subprogress, 0);
		default:
			throw new AssertionError();
		}
	}

	@Override public Iterable<? extends Progress> getSubProgress() {
		// TODO make this check that subprogress is immutable, and if not return a wrapper
		return subprogress;
	}

	@Override public boolean isDone() throws TaskAbortException {
		for (Progress p: subprogress) {
			if (p == null || !p.isDone()) { return false; }
		}
		return true;
	}

	/**
	** {@inheritDoc}
	**
	** This method just checks to see if at least one subprogress is done.
	*/
	@Override public boolean isPartiallyDone() {
		for (Progress p: subprogress) {
			try {
				if (p != null && !p.isDone()) { return true; }
			} catch (TaskAbortException e) {
				continue;
			}
		}
		return false;
	}

	@Override public void join() throws InterruptedException, TaskAbortException {
		int s = 1;
		for (;;) {
			boolean foundnull = false;
			for (Progress p: subprogress) {
				if (p == null) { foundnull = true; continue; }
				p.join();
			}
			if (!foundnull) { break; }
			// yes, this is a really shit way of doing this. but the best alternative I
			// could come up with was the ugly commented-out stuff in ProgressTracker.

			// anyhow, Progress objects are usually created very very soon after its
			// tracking ID is added to the collection backing the Iterable<Progress>,
			// so this shouldn't happen that often.

			Thread.sleep(s*1000);
			// extend the sleep time with reducing probability, or reset it to 1
			if (s == 1 || Math.random() < 1/Math.log(s)/4) { ++s; } else { s = 1; }
		}
	}

	/*
	// moved here from ParallelSerialiser. might be useful.
	protected void joinAll(Iterable<P> plist) throws InterruptedException, TaskAbortException {
		Iterator<P> it = plist.iterator();
		while (it.hasNext()) {
			P p = it.next();
			try {
				p.join();
			} catch (TaskAbortException e) {
				if (e.isError()) {
					throw e;
				} else {
					// TODO perhaps have a handleNonErrorAbort() that can be overridden
					it.remove();
				}
			}
		}
	}
	*/

}