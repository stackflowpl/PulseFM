import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import '../bloc/radio_player/radio_player_bloc.dart';
import '../bloc/radio_player/radio_player_state.dart';
import '../bloc/radio_player/radio_player_event.dart';

class PlayerWidget extends StatelessWidget {
  const PlayerWidget({super.key});

  @override
  Widget build(BuildContext context) {
    return BlocBuilder<RadioPlayerBloc, RadioPlayerState>(
      builder: (context, state) {
        return Container(
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.surface,
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.1),
                blurRadius: 4,
                offset: const Offset(0, -2),
              ),
            ],
          ),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              // Error message if any
              if (state.errorMessage != null)
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 16,
                    vertical: 8,
                  ),
                  color: Theme.of(context).colorScheme.error,
                  child: Row(
                    children: [
                      Icon(
                        Icons.error_outline,
                        color: Theme.of(context).colorScheme.onError,
                        size: 16,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          state.errorMessage!,
                          style: TextStyle(
                            color: Theme.of(context).colorScheme.onError,
                            fontSize: 12,
                          ),
                        ),
                      ),
                      InkWell(
                        onTap: () {
                          context.read<RadioPlayerBloc>().clearError();
                        },
                        child: Icon(
                          Icons.close,
                          color: Theme.of(context).colorScheme.onError,
                          size: 16,
                        ),
                      ),
                    ],
                  ),
                ),
              
              // Main player controls
              Padding(
                padding: const EdgeInsets.all(16.0),
                child: Row(
                  children: [
                    // Station icon
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.primary,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Icon(
                        Icons.radio,
                        color: Colors.white,
                        size: 24,
                      ),
                    ),
                    
                    const SizedBox(width: 12),
                    
                    // Station info
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Text(
                            state.currentStationName,
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(
                              fontWeight: FontWeight.w600,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                          const SizedBox(height: 2),
                          Row(
                            children: [
                              if (state.currentStationCity.isNotEmpty) ...[
                                Icon(
                                  Icons.location_on,
                                  size: 12,
                                  color: Colors.grey[600],
                                ),
                                const SizedBox(width: 4),
                                Text(
                                  state.currentStationCity,
                                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: Colors.grey[600],
                                  ),
                                ),
                              ],
                              if (state.currentTrackInfo.isNotEmpty) ...[
                                if (state.currentStationCity.isNotEmpty)
                                  Text(
                                    ' • ',
                                    style: TextStyle(color: Colors.grey[600]),
                                  ),
                                Expanded(
                                  child: Text(
                                    state.currentTrackInfo,
                                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                      color: Colors.grey[600],
                                    ),
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ],
                            ],
                          ),
                        ],
                      ),
                    ),
                    
                    const SizedBox(width: 12),
                    
                    // Connection status indicator
                    if (!state.isConnected)
                      Padding(
                        padding: const EdgeInsets.only(right: 8),
                        child: Icon(
                          Icons.wifi_off,
                          color: Colors.orange[700],
                          size: 20,
                        ),
                      ),
                    
                    // Play/pause button
                    SizedBox(
                      width: 48,
                      height: 48,
                      child: state.isBuffering
                          ? const CircularProgressIndicator(
                              strokeWidth: 2,
                            )
                          : IconButton(
                              onPressed: () {
                                if (state.isPlaying) {
                                  context.read<RadioPlayerBloc>().add(
                                        const PausePlaybackEvent(),
                                      );
                                } else {
                                  context.read<RadioPlayerBloc>().add(
                                        const ResumePlaybackEvent(),
                                      );
                                }
                              },
                              icon: Icon(
                                state.isPlaying
                                    ? Icons.pause_circle_filled
                                    : Icons.play_circle_filled,
                                size: 36,
                                color: Theme.of(context).colorScheme.primary,
                              ),
                            ),
                    ),
                    
                    // Stop button
                    IconButton(
                      onPressed: () {
                        context.read<RadioPlayerBloc>().add(
                              const StopPlaybackEvent(),
                            );
                      },
                      icon: Icon(
                        Icons.stop_circle_outlined,
                        size: 32,
                        color: Colors.grey[600],
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        );
      },
    );
  }
}