package com.g9A.CPEN431.A9.server;

public class HashSpace {
	
	public int hashStart;
	public int hashEnd;
	
	public HashSpace(int start, int end) {
		hashStart = start;
		hashEnd = end;
	}
	
	@Override
	public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof HashSpace)) return false;

        HashSpace other = (HashSpace) o;

        return other.hashStart == this.hashStart && other.hashEnd == this.hashEnd;
	}

	public boolean inSpace(int hash) {
		return hashStart <= hash && hashEnd >= hash;
	}

	public boolean inSpace(HashSpace hs) {
		return (hs.hashStart >= this.hashStart && hs.hashEnd <= this.hashEnd);
	}
	
	@Override
	public String toString() {
		return hashStart + "-" + hashEnd;
	}
}
