package rotp.addons.mandom.state;

/**
 * Added to every Level by LevelBlockJournalMixin: the level's open Mandom history, or null while no Mandom user is
 * in it. The level points to its history and never the other way, so a stored history cannot keep a level alive.
 */
public interface MandomJournalHolder {
    Object rotpMandom$journal();

    void rotpMandom$setJournal(Object journal);
}
