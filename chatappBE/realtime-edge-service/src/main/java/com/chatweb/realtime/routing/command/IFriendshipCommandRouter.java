package com.chatweb.realtime.routing.command;

/**
 * Routes friendship commands from the edge to friendship-service.
 *
 * Supported command set for Phase E:
 * - send-request
 * - accept-request
 * - decline-request
 * - cancel-request
 * - unfriend
 * - block
 * - unblock
 */
public interface IFriendshipCommandRouter {
    void route(String accessToken, FriendshipCommandRequest commandRequest);
}
