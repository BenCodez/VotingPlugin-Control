package com.bencodez.votingplugin.control.protocol;

/** Safe backend metadata observed by one proxy. Addresses are intentionally excluded. */
public record BackendServerIdentity(String backendId, String displayName, boolean presenceKnown,
                                    boolean available, int playerCount) { }
